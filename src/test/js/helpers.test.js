const test = require('node:test');
const assert = require('node:assert/strict');
const { load } = require('./harness');

// common.js replaces window.BrickTools, so it must be loaded first (as in the browser).
load('common.js');
load('shared/table.js');
load('modules/partscollection-collection-utils.js');

test('compareValues sorts numbers numerically and strings by locale', () => {
  const { compareValues } = BrickTools.Shared;
  assert.ok(compareValues(2, 10, 1) < 0);
  assert.ok(compareValues(10, 2, 1) > 0);
  assert.ok(compareValues('a2', 'a10', 1) < 0);
  assert.ok(compareValues(null, 'x', 1) > 0);
  assert.equal(compareValues('', '', 1), 0);
});

test('sortRows uses the requested key and direction', () => {
  const rows = [{ n: 10 }, { n: 2 }, { n: 1 }];
  const sorted = BrickTools.Shared.sortRows(rows, { key: 'n', dir: 1 });
  assert.deepEqual(sorted.map(r => r.n), [1, 2, 10]);
  assert.deepEqual(rows.map(r => r.n), [10, 2, 1], 'input is not mutated');
  const descending = BrickTools.Shared.sortRows(rows, { key: 'n', dir: -1 });
  assert.deepEqual(descending.map(r => r.n), [10, 2, 1]);
});

test('colorHex and readableTextColor handle short and long hex values', () => {
  const { colorHex, readableTextColor } = BrickTools.Shared;
  assert.equal(colorHex({ rgb: 'F00' }), '#FF0000');
  assert.equal(colorHex({ rgb: '#c91a09' }), '#c91a09');
  assert.equal(colorHex({ rgb: 'not-a-color' }), null);
  assert.equal(readableTextColor('#ffffff'), '#111827');
  assert.equal(readableTextColor('#000000'), '#ffffff');
  assert.equal(readableTextColor('bad'), '#111827');
});

test('parseSizeQuery and sizeMatches interpret part sizes', () => {
  const u = BrickTools.CollectionUtils;
  assert.deepEqual(u.parseSizeQuery('2x4'), ['2', '4']);
  assert.deepEqual(u.parseSizeQuery('2 × 4 x 1/3'), ['2', '4', '1/3']);
  assert.deepEqual(u.parseSizeQuery(''), []);
  assert.equal(u.parseSizeQuery('a brick'), null);
  assert.equal(u.sizeMatches('2x4x1', ['2', '4']), true);
  assert.equal(u.sizeMatches('2x4', ['2', '4', '1']), false);
  assert.equal(u.sizeMatches('2x4x1', []), true);
});

test('collectionTokens and matchesCollectionQuery search all words', () => {
  const u = BrickTools.CollectionUtils;
  const tokens = u.collectionTokens('  Brick  2X4 ');
  assert.deepEqual(tokens, ['brick', '2x4']);
  assert.equal(u.matchesCollectionQuery(['Brick 2 x 4', 'Red'], tokens), false);
  assert.equal(u.matchesCollectionQuery(['Brick 2X4', 'Red'], tokens), true);
});

test('getPartNameFromBOName cleans LEGO prefix, color and trailing set number', () => {
  assert.equal(BrickTools.getPartNameFromBOName('LEGO Brick 2 x 4 Red (7538)', 'Red'), 'Brick 2 x 4');
  assert.equal(BrickTools.getPartNameFromBOName('LEGO Plate 1 x 2 (4175 / 31593)'), 'Plate 1 x 2');
  assert.equal(BrickTools.getPartNameFromBOName(''), '');
});
