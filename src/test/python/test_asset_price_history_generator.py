import csv
import tempfile
import unittest
from datetime import date
from decimal import Decimal
from pathlib import Path

import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / "scripts"))

import generate_asset_price_history as generator


def scale_args(min_observations=3):
    return type(
        "Args",
        (),
        {
            "min_scale_observations": min_observations,
            "scale_error_threshold": Decimal("0.03"),
            "scale_dispersion_threshold": Decimal("0.05"),
            "severe_mismatch_threshold": Decimal("0.15"),
        },
    )()


def exact_match(asset, stooq):
    return generator.MatchDecision(
        asset=asset,
        stooq=stooq,
        status="ACCEPTED_EXACT",
        method="EXACT_SYMBOL",
        confidence="HIGH",
        is_exact_listing=True,
        is_alternate_listing=False,
        original_exchange=generator.asset_exchange(asset),
        matched_exchange=generator.stooq_exchange(stooq),
        original_currency=asset.currency,
        matched_currency=stooq.currency,
        requires_fx_conversion=asset.currency != stooq.currency,
        manual_approval_status="AUTO_ACCEPTED",
    )


class AssetPriceHistoryGeneratorTest(unittest.TestCase):
    def test_stooq_filename_symbol_parsing_and_us_mapping(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "daily" / "us" / "nasdaq stocks" / "aapl.us.txt"
            path.parent.mkdir(parents=True)
            path.write_text("<TICKER>,<PER>,<DATE>,<TIME>,<OPEN>,<HIGH>,<LOW>,<CLOSE>,<VOL>,<OPENINT>\n")
            report = generator.GenerationReport()
            index = generator.index_stooq_files(Path(tmp), report)
            asset = generator.Asset(1, "AAPL.US", "AAPL", "AAPL.US", "US", "USD", "EQUITY", "Apple")
            matches, mappings = generator.match_assets([asset], index, report)
            self.assertEqual("aapl.us", matches[1].stooq.symbol)
            self.assertEqual("USD", mappings[0].price_currency)
            self.assertEqual("ACCEPTED_EXACT", matches[1].status)

    def test_polish_symbol_mapping(self):
        asset = generator.Asset(1, "ALE.PL", "ALE", "ALE.PL", "PL", "PLN", "EQUITY", "Allegro")
        self.assertIn("ale.pl", generator.normalized_exact_stooq_candidates(asset))

    def test_prevents_arbitrary_de_to_us_match(self):
        asset = generator.Asset(1, "DFEN.DE", "DFEN", "DFEN.DE", "DE", "EUR", "ETF", "Defense")
        self.assertNotIn("dfen.us", generator.normalized_exact_stooq_candidates(asset))

        report = generator.GenerationReport()
        matches, _ = generator.match_assets(
            [asset],
            {"dfen.us": generator.StooqFile("dfen.us", Path("dfen.us.txt"), "us/nyse etfs", "USD")},
            report,
        )
        self.assertNotIn(1, matches)
        self.assertEqual("REJECTED_WRONG_EXCHANGE", report.rejected_matches[0].status)

    def test_prevents_ccc_pl_to_us_ticker_match(self):
        asset = generator.Asset(1, "CCC.PL", "CCC", "CCC.PL", "PL", "PLN", "EQUITY", "Coca-Cola CDR")
        report = generator.GenerationReport()
        matches, _ = generator.match_assets(
            [asset],
            {"ccc.us": generator.StooqFile("ccc.us", Path("ccc.us.txt"), "us/nasdaq stocks", "USD")},
            report,
        )
        self.assertNotIn(1, matches)
        self.assertEqual("REJECTED_WRONG_EXCHANGE", report.rejected_matches[0].status)

    def test_us_imported_etf_can_fallback_to_uk_stooq_listing(self):
        asset = generator.Asset(1, "IUVL", "IUVL", "IUVL", "US", "USD", "ETF", "iShares USD Treasury Bond 1-3yr UCITS ETF")
        self.assertNotIn("iuvl.uk", generator.normalized_exact_stooq_candidates(asset))

        report = generator.GenerationReport()
        matches, mappings = generator.match_assets(
            [asset],
            {"iuvl.uk": generator.StooqFile("iuvl.uk", Path("iuvl.uk.txt"), "uk/lse etfs", "GBP")},
            report,
        )
        self.assertEqual("iuvl.uk", matches[1].stooq.symbol)
        self.assertEqual("ACCEPTED_ALTERNATE_LISTING", matches[1].status)
        self.assertEqual("USD", mappings[0].price_currency)

    def test_gbx_gbp_currency_unit_inference_for_uk_symbol(self):
        self.assertEqual("GBP", generator.infer_stooq_currency("vod.uk", Path("daily/uk/vod.uk.txt")))

    def test_duplicate_handling_ignores_exact_duplicate_and_reports_conflict(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "cdr.pl.txt"
            path.write_text(
                "<TICKER>,<PER>,<DATE>,<TIME>,<OPEN>,<HIGH>,<LOW>,<CLOSE>,<VOL>,<OPENINT>\n"
                "CDR.PL,D,20260102,000000,100,101,99,100,10,0\n"
                "CDR.PL,D,20260102,000000,100,101,99,100,10,0\n"
                "CDR.PL,D,20260103,000000,100,101,99,100,10,0\n"
                "CDR.PL,D,20260103,000000,100,102,99,100,10,0\n"
            )
            report = generator.GenerationReport()
            asset = generator.Asset(1, "CDR.PL", "CDR", "CDR.PL", "PL", "PLN", "EQUITY", "CD Projekt")
            stooq = generator.StooqFile("cdr.pl", path, "pl/wse stocks", "PLN")
            rows = generator.stooq_rows(
                asset,
                stooq,
                date(2026, 1, 1),
                date(2026, 1, 31),
                report,
                exact_match(asset, stooq),
                generator.ScaleDecision(Decimal("1"), "ACCEPTED", None, None, 0, None, None),
            )
            self.assertEqual(2, len(rows))
            self.assertEqual(1, len(report.conflicting_duplicates))

    def test_vwap_calculation(self):
        observations = {
            (1, "XTB_TRADE_OPEN", date(2026, 1, 2)): [
                (Decimal("10"), Decimal("2")),
                (Decimal("20"), Decimal("1")),
            ]
        }
        report = generator.GenerationReport()
        assets = {1: generator.Asset(1, "ABC.US", "ABC", "ABC.US", "US", "USD", "EQUITY", "ABC")}
        rows = generator.aggregate_trade_rows(observations, assets, report)
        self.assertEqual(Decimal("13.33333333333333333333333333"), rows[0].close_price)
        self.assertEqual(2, rows[0].observation_count)

    def test_linear_interpolation_max_gap_and_no_extrapolation(self):
        assets = {1: generator.Asset(1, "ABC.US", "ABC", "ABC.US", "US", "USD", "EQUITY", "ABC")}
        rows = [
            generator.PriceRow(1, "ABC.US", date(2026, 1, 2), "XTB_TRADE_OPEN", "ABC.US", "XTB_TRADE_OPEN", "USD", None, None, None, Decimal("10"), None, None, False),
            generator.PriceRow(1, "ABC.US", date(2026, 1, 6), "XTB_TRADE_CLOSE", "ABC.US", "XTB_TRADE_CLOSE", "USD", None, None, None, Decimal("14"), None, None, False),
            generator.PriceRow(1, "ABC.US", date(2026, 3, 1), "XTB_TRADE_OPEN", "ABC.US", "XTB_TRADE_OPEN", "USD", None, None, None, Decimal("20"), None, None, False),
        ]
        report = generator.GenerationReport()
        interpolated = generator.interpolate_trade_rows(rows, assets, 30, Decimal("0.50"), report)
        self.assertEqual([date(2026, 1, 5)], [row.price_date for row in interpolated])
        self.assertEqual(1, len(report.long_gaps))

    def test_sql_escaping_and_idempotent_upsert_output(self):
        self.assertEqual("'Bob''s ETF'", generator.sql_string("Bob's ETF"))
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp) / "out.sql"
            args = type(
                "Args",
                (),
                {
                    "stooq_dir": "stooq",
                    "batch_size": 10,
                    "generated_at": "2026-01-01T00:00:00+00:00",
                },
            )()
            report = generator.GenerationReport()
            report.covered_from = date(2026, 1, 1)
            report.covered_to = date(2026, 1, 2)
            generator.write_sql(
                output,
                [generator.Asset(1, "ABC.US", "ABC", "ABC.US", "US", "USD", "EQUITY", "ABC")],
                [],
                [
                    generator.PriceRow(1, "ABC.US", date(2026, 1, 2), "STOOQ", "abc.us", "STOOQ", "USD", Decimal("1"), Decimal("2"), Decimal("1"), Decimal("1.5"), None, Decimal("10"), False)
                ],
                args,
                report,
            )
            text = output.read_text()
            self.assertIn("on conflict (asset_id, price_date, source) do update", text)
            self.assertIn("where excluded.quality_score >= investory.asset_price_history.quality_score", text)

    def test_trade_vs_stooq_diagnostic(self):
        report = generator.GenerationReport()
        assets = {1: generator.Asset(1, "ABC.US", "ABC", "ABC.US", "US", "USD", "EQUITY", "ABC")}
        observations = {
            (1, "XTB_TRADE_OPEN", date(2026, 1, 2)): [(Decimal("110"), Decimal("1"))]
        }
        generator.compare_trades_to_stooq(
            observations,
            {(1, date(2026, 1, 2)): Decimal("100")},
            assets,
            report,
            Decimal("0.05"),
        )
        self.assertEqual(1, len(report.trade_vs_stooq))

    def test_uk_pence_scale_inferred_from_same_day_trade_prices(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "emim.uk.txt"
            path.write_text(
                "<TICKER>,<PER>,<DATE>,<TIME>,<OPEN>,<HIGH>,<LOW>,<CLOSE>,<VOL>,<OPENINT>\n"
                "EMIM.UK,D,20250214,000000,2800,2810,2790,2809.5,10,0\n"
            )
            asset = generator.Asset(1, "EMIM.UK", "EMIM", "EMIM.UK", "UK", "EUR", "ETF", "EMIM")
            stooq = generator.StooqFile("emim.uk", path, "uk/lse etfs", "EUR")
            observations = {
                (1, "XTB_TRADE_OPEN", date(2025, 2, 14)): [(Decimal("28.15"), Decimal("1"))]
            }
            report = generator.GenerationReport()
            scale = generator.infer_stooq_price_scale(asset, stooq, observations, report, scale_args(1))
            self.assertEqual(Decimal("0.01"), scale.factor)
            rows = generator.stooq_rows(asset, stooq, date(2025, 2, 1), date(2025, 2, 28), report, exact_match(asset, stooq), scale)
            self.assertEqual(Decimal("28.095"), rows[0].close_price)

    def test_stable_10x_non_uk_scale_requires_manual_review(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "nflx.us.txt"
            path.write_text(
                "<TICKER>,<PER>,<DATE>,<TIME>,<OPEN>,<HIGH>,<LOW>,<CLOSE>,<VOL>,<OPENINT>\n"
                "NFLX.US,D,20250102,000000,90,91,89,90,10,0\n"
                "NFLX.US,D,20250103,000000,100,101,99,100,10,0\n"
                "NFLX.US,D,20250106,000000,120,121,119,120,10,0\n"
            )
            asset = generator.Asset(1, "NFLX.US", "NFLX", "NFLX.US", "US", "USD", "EQUITY", "Netflix")
            stooq = generator.StooqFile("nflx.us", path, "us/nasdaq stocks", "USD")
            observations = {
                (1, "XTB_TRADE_OPEN", date(2025, 1, 2)): [(Decimal("900"), Decimal("1"))],
                (1, "XTB_TRADE_OPEN", date(2025, 1, 3)): [(Decimal("1000"), Decimal("1"))],
                (1, "XTB_TRADE_CLOSE", date(2025, 1, 6)): [(Decimal("1200"), Decimal("1"))],
            }
            report = generator.GenerationReport()
            scale = generator.infer_stooq_price_scale(asset, stooq, observations, report, scale_args())
            self.assertEqual("MANUAL_REVIEW", scale.status)
            self.assertEqual(Decimal("10"), scale.factor)
            self.assertEqual(1, len(report.corporate_action_review))

    def test_stable_5x_non_uk_scale_requires_manual_review(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "cvna.us.txt"
            path.write_text(
                "<TICKER>,<PER>,<DATE>,<TIME>,<OPEN>,<HIGH>,<LOW>,<CLOSE>,<VOL>,<OPENINT>\n"
                "CVNA.US,D,20250102,000000,60,61,59,60,10,0\n"
                "CVNA.US,D,20250103,000000,80,81,79,80,10,0\n"
                "CVNA.US,D,20250106,000000,90,91,89,90,10,0\n"
            )
            asset = generator.Asset(1, "CVNA.US", "CVNA", "CVNA.US", "US", "USD", "EQUITY", "Carvana")
            stooq = generator.StooqFile("cvna.us", path, "us/nyse stocks", "USD")
            observations = {
                (1, "XTB_TRADE_OPEN", date(2025, 1, 2)): [(Decimal("300"), Decimal("1"))],
                (1, "XTB_TRADE_OPEN", date(2025, 1, 3)): [(Decimal("400"), Decimal("1"))],
                (1, "XTB_TRADE_CLOSE", date(2025, 1, 6)): [(Decimal("450"), Decimal("1"))],
            }
            report = generator.GenerationReport()
            scale = generator.infer_stooq_price_scale(asset, stooq, observations, report, scale_args())
            self.assertEqual("MANUAL_REVIEW", scale.status)
            self.assertEqual(Decimal("5"), scale.factor)

    def test_unstable_ratio_rejects_candidate(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "abc.us.txt"
            path.write_text(
                "<TICKER>,<PER>,<DATE>,<TIME>,<OPEN>,<HIGH>,<LOW>,<CLOSE>,<VOL>,<OPENINT>\n"
                "ABC.US,D,20250102,000000,100,101,99,100,10,0\n"
                "ABC.US,D,20250103,000000,100,101,99,100,10,0\n"
                "ABC.US,D,20250106,000000,100,101,99,100,10,0\n"
            )
            asset = generator.Asset(1, "ABC.US", "ABC", "ABC.US", "US", "USD", "EQUITY", "ABC")
            stooq = generator.StooqFile("abc.us", path, "us/nyse stocks", "USD")
            observations = {
                (1, "XTB_TRADE_OPEN", date(2025, 1, 2)): [(Decimal("100"), Decimal("1"))],
                (1, "XTB_TRADE_OPEN", date(2025, 1, 3)): [(Decimal("300"), Decimal("1"))],
                (1, "XTB_TRADE_CLOSE", date(2025, 1, 6)): [(Decimal("600"), Decimal("1"))],
            }
            report = generator.GenerationReport()
            scale = generator.infer_stooq_price_scale(asset, stooq, observations, report, scale_args())
            self.assertEqual("REJECTED_UNSTABLE_SCALE", scale.status)

    def test_account_currency_does_not_block_exact_us_price_currency(self):
        asset = generator.Asset(1, "INTC.US", "INTC", "INTC.US", "US", "PLN", "EQUITY", "Intel")
        report = generator.GenerationReport()
        matches, mappings = generator.match_assets(
            [asset],
            {"intc.us": generator.StooqFile("intc.us", Path("intc.us.txt"), "us/nasdaq stocks", "USD")},
            report,
        )
        self.assertEqual("ACCEPTED_EXACT", matches[1].status)
        self.assertTrue(mappings[0].requires_fx_conversion)
        self.assertEqual("USD", mappings[0].matched_currency)


if __name__ == "__main__":
    unittest.main()
