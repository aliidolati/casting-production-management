const { test } = require('node:test');
const assert = require('node:assert/strict');
const { statistics } = require('../../main/resources/static/js/spectrometry-charts.js');

const dataset = (...pairs) => pairs.map(([day, mg]) => ({ day: 20800 + day, mg: String(mg) }));
const approx = (actual, expected) => assert.ok(Math.abs(actual - expected) < 1e-12, `${actual} ≠ ${expected}`);

test('least squares uses elapsed days, including irregular and repeated dates', () => {
    const stats = statistics(dataset([0, 1], [1, 1.2], [1, 1.2], [9, 2.8]), 'mg', 0.0002);
    approx(stats.mean, 1.55);
    approx(stats.slope, 0.2);
    approx(stats.r2, 1);
    approx(stats.start.value, 1);
    approx(stats.end.value, 2.8);
    assert.equal(stats.direction, 'increasing');

    const repeated = statistics(dataset([0, 0], [0, 2], [2, 4]), 'mg', 0.0002);
    approx(repeated.mean, 2);
    approx(repeated.slope, 1.5);
    approx(repeated.r2, 0.75);
});

test('direction depends on slope and the element threshold, independently of R²', () => {
    const data = dataset([0, 1], [5, 0.9995], [10, 0.999]);
    const magnesium = statistics(data, 'mg', 0.0002);
    const titanium = statistics(data, 'mg', 0.00005);
    approx(magnesium.r2, 1);
    assert.equal(magnesium.direction, 'stable');
    assert.equal(titanium.direction, 'decreasing');
});

test('empty, single, same-date and constant series keep undefined statistics distinct', () => {
    assert.equal(statistics([], 'mg', 0.0002).mean, null);
    for (const rows of [dataset([0, 0]), dataset([0, 0], [0, 2])]) {
        const stats = statistics(rows, 'mg', 0.0002);
        assert.equal(stats.reason, 'dates');
        assert.equal(stats.slope, null);
        assert.equal(stats.r2, null);
    }
    const constant = statistics(dataset([0, '0.1234567891'], [10, '0.1234567891'], [15, '0.1234567891']), 'mg', 0.0002);
    assert.equal(constant.direction, 'stable');
    assert.equal(constant.slope, 0);
    assert.equal(constant.r2, null);
    assert.equal(constant.mean, 0.1234567891);
});
