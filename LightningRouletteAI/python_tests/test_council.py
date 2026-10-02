# -*- coding: utf-8 -*-
"""Python meclisi testleri: sızıntı yok, canlı = replay, determinizm, geri alma, durum kaydı, öğrenme/aşırı iddia."""
import json
import math
import os
import sys
import time
import unittest

import numpy as np

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "python"))
import lr_council as lc  # noqa: E402


def j(a):
    return json.dumps([int(x) for x in a])


def replay(v, warm, rec, steps):
    lc.replay_begin(j(v), warm)
    b = lc.replay_chunk(rec, steps)
    return np.frombuffer(b, dtype=np.float32).reshape(-1, 8, 37)


def sticky(n, seed, stay):
    r = np.random.RandomState(seed)
    out = []
    s = r.randint(lc.NS)
    for _ in range(n):
        if r.rand() > stay:
            s = r.randint(lc.NS)
        members = [x for x in range(37) if lc.SEC[x] == s]
        out.append(members[r.randint(len(members))])
    return np.array(out)


class CouncilTest(unittest.TestCase):
    def setUp(self):
        lc.set_sectors([0, 5, 9, 13, 17, 21, 25, 29, 33, 37])
        lc.configure("{}")

    def test_selftest_and_shapes(self):
        self.assertEqual(lc.selftest(), "OK")
        r = json.loads(lc.live_predict(j(np.random.RandomState(1).randint(0, 37, 120))))
        self.assertEqual(len(r["ids"]), 8)
        self.assertEqual(r["ids"], ["lstm", "transformer", "cnn", "gboost", "hmm", "knn_dtw", "context", "motif"])
        P = np.array(r["probs"])
        self.assertEqual(P.shape, (8, 37))
        self.assertTrue(np.all(np.isfinite(P)) and np.all(P > 0))
        self.assertTrue(np.allclose(P.sum(axis=1), 1.0, atol=1e-6))

    def test_future_cannot_change_past_predictions(self):
        rng = np.random.RandomState(1)
        v = rng.randint(0, 37, 260)
        a = replay(v, 0, 150, 200)
        v2 = v.copy()
        v2[200:] = rng.randint(0, 37, 60)
        b = replay(v2, 0, 150, 200)
        self.assertEqual(a.shape, (50, 8, 37))
        self.assertTrue(np.array_equal(a, b), "gelecek değerler geçmiş tahmini değiştirdi (SIZINTI)")

    def test_hist_is_physically_cut(self):
        v = np.arange(37)
        h = lc.Hist(v, 10)
        with self.assertRaises(IndexError):
            _ = h.v[10]

    def test_live_equals_replay(self):
        v = np.random.RandomState(2).randint(0, 37, 215)
        live = [json.loads(lc.live_predict(j(v[:n])))["probs"] for n in range(150, 200)]
        rp = replay(v, 0, 150, 200)
        np.testing.assert_allclose(np.array(live), rp, atol=3e-6)

    def test_deterministic(self):
        v = np.random.RandomState(3).randint(0, 37, 200)
        self.assertTrue(np.array_equal(replay(v, 0, 120, 200), replay(v, 0, 120, 200)))

    def test_undo_restores_previous_state(self):
        v = np.random.RandomState(4).randint(0, 37, 240)
        lc.live_predict(j(v[:200]))
        lc.live_predict(j(v[:201]))
        back = np.array(json.loads(lc.live_predict(j(v[:200])))["probs"])     # son spin silindi → snapshot geri yüklenir
        self.assertEqual(lc.state_n(), 200)
        lc.configure("{}")
        fresh = np.array(json.loads(lc.live_predict(j(v[:200])))["probs"])
        np.testing.assert_allclose(back, fresh, atol=1e-7)

    def test_history_edit_triggers_rebuild(self):
        rng = np.random.RandomState(5)
        v = rng.randint(0, 37, 180)
        lc.live_predict(j(v))
        v2 = v.copy()
        v2[-3] = (v2[-3] + 5) % 37
        a = np.array(json.loads(lc.live_predict(j(v2)))["probs"])
        lc.configure("{}")
        b = np.array(json.loads(lc.live_predict(j(v2)))["probs"])
        np.testing.assert_allclose(a, b, atol=1e-7)

    def test_state_save_load_roundtrip(self):
        v = np.random.RandomState(6).randint(0, 37, 220)
        lc.live_predict(j(v[:200]))
        blob = bytes(lc.state_save())
        cont = np.array(json.loads(lc.live_predict(j(v[:210])))["probs"])
        lc.configure("{}")
        self.assertTrue(lc.state_load(blob))
        self.assertEqual(lc.state_n(), 200)
        loaded = np.array(json.loads(lc.live_predict(j(v[:210])))["probs"])
        np.testing.assert_allclose(cont, loaded, atol=1e-9)
        self.assertFalse(lc.state_load(b"not-a-pickle"))

    def test_random_data_is_not_overconfident(self):
        v = np.random.RandomState(7).randint(0, 37, 700)
        P = replay(v, 0, 400, 700)
        ll = -np.log(np.stack([P[i, :, v[400 + i]] for i in range(len(P))]))
        base = math.log(37)
        for m, name in enumerate(["lstm", "transformer", "cnn", "gboost", "hmm", "knn_dtw", "context", "motif"]):
            self.assertLess(ll[:, m].mean(), base + 0.05, f"{name} rastgele veride aşırı güvenli: {ll[:, m].mean():.4f} vs {base:.4f}")

    def test_structure_is_learned_when_present(self):
        v = sticky(1500, 8, 0.55)
        P = replay(v, 0, 900, 1500)
        ll = -np.log(np.stack([P[i, :, v[900 + i]] for i in range(len(P))])).mean(axis=0)
        base = math.log(37)
        names = ["lstm", "transformer", "cnn", "gboost", "hmm", "knn_dtw", "context", "motif"]
        best = {n: base - x for n, x in zip(names, ll)}
        self.assertGreater(max(best["context"], best["motif"], best["hmm"]), 0.02, f"yapı öğrenilmedi: {best}")

    def test_sliding_window_with_offset_matches_full_history(self):
        """Kotlin yalnızca son pencereyi (offset ile) gönderir; durum her adımda tutarlı kalmalı, yeniden kurulum tetiklenmemeli."""
        v = np.random.RandomState(12).randint(0, 37, 330)
        W = 120
        lc.configure("{}")
        rebuilds = 0
        orig = lc.Council.reset

        def counting(self):
            nonlocal rebuilds
            rebuilds += 1
            orig(self)
        lc.Council.reset = counting
        try:
            for n in range(150, 200):
                lo = max(0, n - W)
                json.loads(lc.live_predict(j(v[lo:n]), lo))
        finally:
            lc.Council.reset = orig
        self.assertEqual(rebuilds, 1, "ilk kurulum dışında yeniden kurulum olmamalı")
        self.assertEqual(lc.state_n(), 199)

    def test_sector_config(self):
        lc.set_sectors([0, 10, 20, 37])
        self.assertEqual(lc.NS, 3)
        lc.configure(json.dumps({"bounds": [0, 10, 20, 37]}))
        v = np.random.RandomState(9).randint(0, 37, 150)
        P = np.array(json.loads(lc.live_predict(j(v)))["probs"])
        self.assertEqual(P.shape, (8, 37))
        with self.assertRaises(ValueError):
            lc.set_sectors([0, 5, 4, 37])

    def test_speed_budget(self):
        v = np.random.RandomState(10).randint(0, 37, 330)
        t0 = time.time()
        replay(v, 0, 30, 330)
        per = (time.time() - t0) / 330
        print(f"\n  adım başına ≈ {per * 1000:.1f} ms (8 üye predict+update)")
        self.assertLess(per, 0.08)


if __name__ == "__main__":
    unittest.main(verbosity=2)
