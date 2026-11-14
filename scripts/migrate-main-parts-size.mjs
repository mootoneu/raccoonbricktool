#!/usr/bin/env node
/**
 * Adds the Size column to data/bt/main_parts.csv and fills it from each part name.
 *
 * A size is written as <width>x<length>x<height>. When the name only has two dimensions, the
 * height defaults to 1/3 for plates and tiles and to 1 for the other categories. Parts whose name
 * has no size keep an empty Size cell.
 *
 * Usage:
 *   node scripts/migrate-main-parts-size.mjs [main-parts.csv] [part-categories.csv]
 */
import { readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const dataDirectory = path.join(root, 'data', 'bt');
const mainPartsFile = process.argv[2]
  ? path.resolve(process.argv[2])
  : path.join(dataDirectory, 'main_parts.csv');
const categoriesFile = process.argv[3]
  ? path.resolve(process.argv[3])
  : path.join(dataDirectory, 'part_categories.csv');

const DIMENSION = '(?:\\d+\\s+\\d+/\\d+|\\d+/\\d+|\\d+(?:\\.\\d+)?)';
const SIZE = new RegExp(
  `(${DIMENSION})\\s*[x×]\\s*(${DIMENSION})(?:\\s*[x×]\\s*(${DIMENSION}))?`, 'i');

function parseCsv(text) {
  const rows = [];
  let row = [];
  let field = '';
  let quoted = false;
  let index = 0;
  while (index < text.length) {
    const character = text[index];
    if (quoted) {
      if (character === '"') {
        if (text[index + 1] === '"') {
          field += '"';
          index += 2;
          continue;
        }
        quoted = false;
        index += 1;
        continue;
      }
      field += character;
      index += 1;
      continue;
    }
    if (character === '"') {
      quoted = true;
      index += 1;
      continue;
    }
    if (character === ',') {
      row.push(field);
      field = '';
      index += 1;
      continue;
    }
    if (character === '\r' || character === '\n') {
      if (character === '\r' && text[index + 1] === '\n') {
        index += 1;
      }
      row.push(field);
      rows.push(row);
      row = [];
      field = '';
      index += 1;
      continue;
    }
    field += character;
    index += 1;
  }
  if (field.length > 0 || row.length > 0) {
    row.push(field);
    rows.push(row);
  }
  return rows;
}

function writeCsv(rows) {
  return rows.map(function (row) {
    return row.map(function (value) {
      return /[",\r\n]/.test(value) ? '"' + value.replace(/"/g, '""') + '"' : value;
    }).join(',');
  }).join('\r\n') + '\r\n';
}

function defaultHeight(group) {
  let normalized = String(group == null ? '' : group).trim().toLowerCase();
  if (normalized.endsWith('s')) {
    normalized = normalized.slice(0, -1);
  }
  return normalized === 'plate' || normalized === 'tile' ? '1/3' : '1';
}

function parseSize(name, group) {
  if (!name || !name.trim()) {
    return '';
  }
  const match = SIZE.exec(name);
  if (!match) {
    return '';
  }
  const dimension = function (value) { return value.trim().replace(/\s+/g, ' '); };
  const height = match[3] == null ? defaultHeight(group) : dimension(match[3]);
  return dimension(match[1]) + 'x' + dimension(match[2]) + 'x' + height;
}

const categoryRows = parseCsv(await readFile(categoriesFile, 'utf8'));
const categoryHeader = categoryRows.shift();
const categoryIdIndex = categoryHeader.indexOf('Id');
const groupIndex = categoryHeader.indexOf('Group');
const groups = new Map();
categoryRows.forEach(function (row) {
  const id = String(row[categoryIdIndex] || '').trim();
  if (id) {
    groups.set(id, String(row[groupIndex] || '').trim());
  }
});

const rows = parseCsv(await readFile(mainPartsFile, 'utf8'));
const header = rows.shift();
const nameIndex = header.indexOf('Name');
let categoryIndex = header.indexOf('CategoryId');
let sizeIndex = header.indexOf('Size');
if (nameIndex === -1 || categoryIndex === -1) {
  throw new Error('The main parts file must have Name and CategoryId columns.');
}
if (sizeIndex === -1) {
  sizeIndex = nameIndex + 1;
  header.splice(sizeIndex, 0, 'Size');
  rows.forEach(function (row) { row.splice(sizeIndex, 0, ''); });
  categoryIndex = header.indexOf('CategoryId');
}

let sized = 0;
rows.forEach(function (row) {
  const size = parseSize(row[nameIndex], groups.get(String(row[categoryIndex] || '').trim()));
  row[sizeIndex] = size;
  if (size) {
    sized += 1;
  }
});

await writeFile(mainPartsFile, writeCsv([header, ...rows]), 'utf8');
console.log(`Migrated ${rows.length} parts in ${path.relative(root, mainPartsFile)}: `
  + `${sized} with a size, ${rows.length - sized} without.`);
