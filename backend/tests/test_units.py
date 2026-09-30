"""Tests for the unit normalizer."""

from app.pipeline.units import normalize_metric, parse_value_unit, normalize_text_metric


class TestNormalizeMetric:
    """Tests for SI prefix normalization."""

    def test_pico_watts(self):
        value, unit = normalize_metric(8.3, "pW")
        assert abs(value - 8.3e-12) < 1e-20
        assert unit == "W"

    def test_nano_amps(self):
        value, unit = normalize_metric(3.5, "nA")
        assert abs(value - 3.5e-9) < 1e-18
        assert unit == "A"

    def test_micro_amps(self):
        value, unit = normalize_metric(1.2, "µA")
        assert abs(value - 1.2e-6) < 1e-15
        assert unit == "A"

    def test_micro_amps_ascii(self):
        value, unit = normalize_metric(1.2, "uA")
        assert abs(value - 1.2e-6) < 1e-15
        assert unit == "A"

    def test_milli_volts(self):
        value, unit = normalize_metric(185.0, "mV")
        assert abs(value - 0.185) < 1e-10
        assert unit == "V"

    def test_nano_seconds(self):
        value, unit = normalize_metric(0.42, "ns")
        assert abs(value - 4.2e-10) < 1e-18
        assert unit == "s"

    def test_nano_meters(self):
        value, unit = normalize_metric(45.0, "nm")
        assert abs(value - 45e-9) < 1e-15
        assert unit == "m"

    def test_kilo_hertz(self):
        value, unit = normalize_metric(100.0, "kHz")
        assert abs(value - 100000.0) < 1e-6
        assert unit == "Hz"

    def test_mega_hertz(self):
        value, unit = normalize_metric(2.4, "MHz")
        assert abs(value - 2.4e6) < 1e-3
        assert unit == "Hz"

    def test_giga_hertz(self):
        value, unit = normalize_metric(1.5, "GHz")
        assert abs(value - 1.5e9) < 1.0
        assert unit == "Hz"

    def test_femto_farads(self):
        value, unit = normalize_metric(12.5, "fF")
        assert abs(value - 12.5e-15) < 1e-22
        assert unit == "F"

    def test_base_unit_no_prefix(self):
        value, unit = normalize_metric(0.6, "V")
        assert value == 0.6
        assert unit == "V"

    def test_passthrough_percent(self):
        value, unit = normalize_metric(92.5, "%")
        assert value == 92.5
        assert unit == "%"

    def test_passthrough_decibel(self):
        value, unit = normalize_metric(15.0, "dB")
        assert value == 15.0
        assert unit == "dB"

    def test_passthrough_dbm(self):
        value, unit = normalize_metric(-10.0, "dBm")
        assert value == -10.0
        assert unit == "dBm"

    def test_compound_unit_pW_per_cell(self):
        value, unit = normalize_metric(8.3, "pW/cell")
        assert abs(value - 8.3e-12) < 1e-20
        assert unit == "W/cell"

    def test_compound_unit_nA_per_cell(self):
        value, unit = normalize_metric(2.5, "nA/cell")
        assert abs(value - 2.5e-9) < 1e-18
        assert unit == "A/cell"

    def test_empty_unit(self):
        value, unit = normalize_metric(42.0, "")
        assert value == 42.0
        assert unit == ""

    def test_unknown_unit_passthrough(self):
        value, unit = normalize_metric(3.14, "rad")
        assert value == 3.14
        assert unit == "rad"


class TestParseValueUnit:
    """Tests for parsing value+unit from text strings."""

    def test_simple_value_unit(self):
        result = parse_value_unit("8.3 pW")
        assert result is not None
        assert result[0] == 8.3
        assert result[1] == "pW"

    def test_no_space(self):
        result = parse_value_unit("185mV")
        assert result is not None
        assert result[0] == 185.0
        assert result[1] == "mV"

    def test_compound_unit(self):
        result = parse_value_unit("8.3 pW/cell")
        assert result is not None
        assert result[0] == 8.3
        assert result[1] == "pW/cell"

    def test_scientific_notation(self):
        result = parse_value_unit("1.2e-6 A")
        assert result is not None
        assert abs(result[0] - 1.2e-6) < 1e-15
        assert result[1] == "A"

    def test_integer(self):
        result = parse_value_unit("45 nm")
        assert result is not None
        assert result[0] == 45.0
        assert result[1] == "nm"

    def test_percentage(self):
        result = parse_value_unit("92.5%")
        assert result is not None
        assert result[0] == 92.5
        assert result[1] == "%"

    def test_no_match(self):
        result = parse_value_unit("no numbers here")
        assert result is None

    def test_empty_string(self):
        result = parse_value_unit("")
        assert result is None


class TestNormalizeTextMetric:
    """Tests for the combined parse+normalize function."""

    def test_full_pipeline(self):
        result = normalize_text_metric("8.3 pW/cell")
        assert result is not None
        raw_val, raw_unit, norm_val, norm_unit = result
        assert raw_val == 8.3
        assert raw_unit == "pW/cell"
        assert abs(norm_val - 8.3e-12) < 1e-20
        assert norm_unit == "W/cell"

    def test_unparseable(self):
        result = normalize_text_metric("not a metric")
        assert result is None
