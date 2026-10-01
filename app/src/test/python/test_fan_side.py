# -*- coding: utf-8 -*-
"""FAN SUPER v1.3 — Python yan meclisi + replay/leakage/persistence testleri.

Çalıştırma:  cd app/src/main/python && python ../../test/python/test_fan_side.py -v
"""
import json
import os
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
PY_SRC = os.path.normpath(os.path.join(HERE, "..", "..", "main", "python"))
if PY_SRC not in sys.path:
    sys.path.insert(0, PY_SRC)

import numpy as np  # noqa: E402

import fan_side as fsd  # noqa: E402
import fan_super as fs  # noqa: E402

sys.path.insert(0, HERE)
from test_fan_super import load_records  # noqa: E402


def sides(vals):
    """İç değer 0..3 (sayı 1..4) → bs (3,4 BÜYÜK), oe (1,3 TEK)."""
    bs = [1 if v >= 2 else 0 for v in vals]
    oe = [1 if v % 2 == 0 else 0 for v in vals]
    return bs, oe


def lj(x):
    return json.dumps(x)


class SideTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        v, t = load_records(260)
        cls.v, cls.t = v, t
        cls.bs, cls.oe = sides(v)

    def test_01_outputs_are_distributions_for_both_axes(self):
        r = json.loads(fs.side_replay(lj(self.bs), lj(self.oe), lj(self.t), True))
        self.assertEqual(len(r["steps"]), len(self.bs))
        self.assertEqual(r["ids"], ["py_side_kalip", "py_side_ngram", "py_side_logit"])
        for st in r["steps"][::17]:
            for m in st["members"]:
                self.assertAlmostEqual(sum(m["bs"]), 1.0, places=4)
                self.assertAlmostEqual(sum(m["oe"]), 1.0, places=4)
                self.assertAlmostEqual(sum(m["comb"]), 1.0, places=4)
                self.assertEqual((len(m["bs"]), len(m["oe"]), len(m["comb"])), (2, 2, 4))

    def test_02_kalip_2_0_learns_patterns_on_BOTH_side_axes(self):
        # BS dizisi periyodu 7, OE dizisi periyodu 5 olan deterministik veri
        n = 400
        bs = [(1 if (i % 7) in (0, 1, 3) else 0) for i in range(n)]
        oe = [(1 if (i % 5) in (0, 2) else 0) for i in range(n)]
        c = fsd.SideCouncil()
        hits = {"bs": 0, "oe": 0}
        cnt = 0
        for i in range(n):
            per, _ = c.predict()
            if i >= 200:
                cnt += 1
                hits["bs"] += int(np.argmax(per[0]["bs"]) == bs[i])
                hits["oe"] += int(np.argmax(per[0]["oe"]) == oe[i])
            c.learn(bs[i], oe[i])
        self.assertGreater(hits["bs"] / cnt, 0.9)
        self.assertGreater(hits["oe"] / cnt, 0.9)

    def test_03_side_is_not_derived_from_numbers(self):
        # Aynı yan dizileri, farklı "sayılar" → aynı çıktı: modül sayıyı hiç görmez.
        c1, c2 = fsd.SideCouncil(), fsd.SideCouncil()
        for b, o in zip(self.bs[:80], self.oe[:80]):
            p1, _ = c1.predict(); p2, _ = c2.predict()
            for a, b2 in zip(p1, p2):
                for ax in fsd.AXES:
                    np.testing.assert_allclose(a[ax], b2[ax])
            c1.learn(b, o, 1); c2.learn(b, o, 999)

    def test_04_future_data_never_leaks_prefix_invariance(self):
        a_bs, a_oe = list(self.bs), list(self.oe)
        b_bs, b_oe = list(self.bs), list(self.oe)
        for i in range(150, len(b_bs)):          # 150'den sonrası tamamen farklı
            b_bs[i] = 1 - b_bs[i]
            b_oe[i] = 1 - b_oe[i]
        ra = json.loads(fs.side_replay(lj(a_bs), lj(a_oe), "[]", True))
        rb = json.loads(fs.side_replay(lj(b_bs), lj(b_oe), "[]", True))
        for i in range(151):                      # adım 150, kayıt 0..149'u bilir
            self.assertEqual(ra["steps"][i], rb["steps"][i], "adım %d sızdırdı" % i)
        self.assertNotEqual(ra["steps"][-1], rb["steps"][-1])

    def test_05_replay_equals_incremental_steps(self):
        r = json.loads(fs.side_replay(lj(self.bs[:120]), lj(self.oe[:120]), "[]", True))
        fs.side_replay("[]", "[]", "[]", False)
        last = None
        for b, o in zip(self.bs[:120], self.oe[:120]):
            last = json.loads(fs.side_step(b, o))
        self.assertEqual(last["members"], r["next"]["members"])
        self.assertEqual(last["mix"], r["next"]["mix"])

    def test_06_sandbox_replay_does_not_touch_live_state(self):
        fs.side_replay(lj(self.bs[:60]), lj(self.oe[:60]), "[]", False)
        before = fs.side_next()
        fs.side_replay(lj(self.bs[:200]), lj(self.oe[:200]), "[]", True)
        self.assertEqual(fs.side_next(), before)
        self.assertEqual(fs._side.S.n, 60)

    def test_07_undo_restores_exact_prediction(self):
        fs.side_replay(lj(self.bs[:100]), lj(self.oe[:100]), "[]", False)
        base = fs.side_next()
        for b, o in zip(self.bs[100:104], self.oe[100:104]):
            fs.side_step(b, o)
        for n in (103, 102, 101, 100):
            r = fs.side_undo_to(n)
            self.assertTrue(r, "undo %d" % n)
        self.assertEqual(json.loads(fs.side_next())["members"], json.loads(base)["members"])
        self.assertEqual(fs._side.S.n, 100)

    def test_08_save_load_processes_only_new_records(self):
        fs.side_replay(lj(self.bs[:150]), lj(self.oe[:150]), "[]", False)
        with tempfile.TemporaryDirectory() as d:
            p = os.path.join(d, "side.pkl")
            self.assertEqual(fs.side_save(p), "ok")
            fs.side_replay("[]", "[]", "[]", False)       # belleği boşalt
            r = json.loads(fs.side_load(p, lj(self.bs[:200]), lj(self.oe[:200])))
            self.assertEqual(r["cached"], 150)
            full = json.loads(fs.side_replay(lj(self.bs[:200]), lj(self.oe[:200]), "[]", True))
            self.assertEqual(r["members"], full["next"]["members"])
            # farklı geçmiş → önek değil → reddedilir
            bad = list(self.bs[:200]); bad[3] = 1 - bad[3]
            self.assertEqual(fs.side_load(p, lj(bad), lj(self.oe[:200])), "")


class CouncilReplayTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.v, cls.t = load_records(130)
        fs.configure(json.dumps({"window": 100, "dl": False}))

    def test_01_replay_members_shape_and_sandbox_isolated(self):
        fs.replay(lj(self.v[:90]), lj(self.t[:90]))
        live = fs.member_info()
        sb = json.loads(fs.sandbox_replay(lj(self.v), lj(self.t)))
        self.assertEqual(len(sb["members"]), len(self.v))
        self.assertEqual(len(sb["members"][0]), len(sb["ids"]))
        self.assertEqual(fs.member_info(), live)           # canlı durum değişmedi
        self.assertEqual(len(fs._council.vals), 90)

    def test_02_member_info_matches_mix(self):
        fs.replay(lj(self.v[:100]), lj(self.t[:100]))
        mi = json.loads(fs.member_info())
        mix = np.zeros(4)
        for p, s in zip(mi["preds"], mi["share"]):
            mix += s * np.asarray(p)
        np.testing.assert_allclose(mix / mix.sum(), fs._council.last_mix, atol=1e-4)

    def test_03_load_state_with_new_records_steps_only_the_tail(self):
        fs.replay(lj(self.v[:100]), lj(self.t[:100]))
        with tempfile.TemporaryDirectory() as d:
            p = os.path.join(d, "py.pkl")
            fs.save_state(p)
            fs.replay("[]", "[]")
            r = json.loads(fs.load_state(p, lj(self.v[:112]), lj(self.t[:112])))
            self.assertEqual(r["cached"], 100)
            self.assertEqual(len(r["tail_members"]), 12)
            self.assertEqual(len(r["per"]), 112)
            full = json.loads(fs.replay(lj(self.v[:112]), lj(self.t[:112]), True))
            np.testing.assert_allclose(r["next"], full["next"], atol=1e-9)
            for a, b in zip(r["tail_members"], full["members"][100:]):
                np.testing.assert_allclose(a, b, atol=1e-5)


if __name__ == "__main__":
    unittest.main()
