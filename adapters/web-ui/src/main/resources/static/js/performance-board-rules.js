(function (root, factory) {
    if (typeof module === 'object' && module.exports) {
        module.exports = factory();
    } else {
        root.PerformanceBoardRules = factory();
    }
}(typeof globalThis === 'object' ? globalThis : this, function () {
    function group(label, aggregation) {
        const month = Number(label.substring(5, 7));
        return aggregation === 'annual' ? label.substring(0, 4)
            : aggregation === 'quarterly' ? label.substring(0, 4) + '-Q' + Math.ceil(month / 3)
            : label;
    }

    function aggregate(values, labels, aggregation, returnValues) {
        const buckets = new Map();
        labels.forEach((label, index) => {
            const key = group(label, aggregation);
            const bucket = buckets.get(key) || [];
            bucket.push(values[index]);
            buckets.set(key, bucket);
        });
        return [...buckets.entries()].map(([label, valuesForPeriod]) => ({
            label,
            value: returnValues ? compound(valuesForPeriod) : valuesForPeriod.reduce((sum, value) => sum + Number(value || 0), 0)
        }));
    }

    function compound(values) {
        let factor = 1;
        let available = false;
        values.forEach(value => {
            if (value != null && Number.isFinite(Number(value))) {
                factor *= 1 + Number(value) / 100;
                available = true;
            }
        });
        return available ? (factor - 1) * 100 : null;
    }

    function periodValues(curve) {
        return curve.map((value, index) => Number(value || 0) - Number(index ? curve[index - 1] || 0 : 0));
    }

    function periodFromCumulativeReturn(curve) {
        return curve.map((value, index) => {
            if (value == null) return null;
            const prior = index ? curve[index - 1] : 0;
            return ((1 + Number(value) / 100) / (1 + Number(prior || 0) / 100) - 1) * 100;
        });
    }

    function cumulativeReturn(series, labels) {
        let factor = 1;
        return labels.map((_, index) => {
            let capital = 0;
            let weightedReturn = 0;
            series.forEach(account => {
                const opening = Number(account.returnCapitalCurve?.[index]);
                const monthlyReturn = Number(account.returnPctCurve?.[index]);
                if (Number.isFinite(opening) && Number.isFinite(monthlyReturn) && opening !== 0) {
                    capital += opening;
                    weightedReturn += opening * monthlyReturn / 100;
                }
            });
            if (capital === 0) return null;
            const periodReturn = weightedReturn / capital;
            factor = periodReturn <= -1 ? 0 : factor * (1 + periodReturn);
            return (factor - 1) * 100;
        });
    }

    function accountData(selectedSeries, allSelected, metric, style, aggregation, labels, names) {
        const cumulative = metric === 'return'
            ? series => cumulativeReturn(series, labels)
            : series => series[0].portfolioCurve || [];
        const period = metric === 'return'
            ? series => periodFromCumulativeReturn(cumulative(series))
            : series => periodValues(series[0].portfolioCurve || []);
        const source = style === 'line' ? cumulative : period;
        const namedSeries = allSelected
            ? [{ label: 'Portfolio', values: source(selectedSeries) }]
            : selectedSeries.map(series => ({ label: names.get(Number(series.id)) || String(series.id), values: source([series]) }));
        return namedSeries.map(series => ({
            label: series.label,
            values: style === 'bars' ? aggregate(series.values, labels, aggregation, metric === 'return') : series.values
        }));
    }

    return { group, aggregate, periodValues, periodFromCumulativeReturn, cumulativeReturn, accountData };
}));
