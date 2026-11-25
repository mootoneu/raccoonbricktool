/**
 * Parts Collection - pure helpers for collection search and drawer content rules.
 * Kept free of page state so they can be unit tested independently.
 */
(function () {
  'use strict';

  function collectionTokens(value) {
    return String(value == null ? '' : value)
      .toLowerCase()
      .split(/\s+/)
      .filter(function (token) { return token.length > 0; });
  }

  function matchesCollectionQuery(values, tokens) {
    const haystack = values
      .map(function (value) { return String(value == null ? '' : value); })
      .join(' ')
      .toLowerCase();
    return tokens.every(function (token) { return haystack.indexOf(token) !== -1; });
  }

  function normalizeSizeText(value) {
    return String(value == null ? '' : value)
      .toLowerCase()
      .replace(/[×*]/g, 'x')
      .replace(/\s*x\s*/g, 'x')
      .replace(/\s+/g, ' ')
      .trim();
  }

  function sizeDimensions(value) {
    const normalized = normalizeSizeText(value);
    return normalized ? normalized.split('x').map(function (part) { return part.trim(); }) : [];
  }

  /**
   * Parse the size filter: an empty array for no filter, null for non-size text,
   * or the entered dimensions.
   */
  function parseSizeQuery(value) {
    const dimensions = sizeDimensions(value);
    if (dimensions.length === 0) {
      return [];
    }
    if (dimensions.length < 2 || dimensions.length > 3) {
      return null;
    }
    const valid = dimensions.every(function (dimension) {
      return /^(?:\d+\s+\d+\/\d+|\d+\/\d+|\d+(?:\.\d+)?)$/.test(dimension);
    });
    return valid ? dimensions : null;
  }

  /** Entering only the first dimensions matches every height ("2x4" matches "2x4x1"). */
  function sizeMatches(size, dimensions) {
    if (!dimensions || dimensions.length === 0) {
      return true;
    }
    const parts = sizeDimensions(size);
    if (parts.length < dimensions.length) {
      return false;
    }
    return dimensions.every(function (dimension, index) { return dimension === parts[index]; });
  }

  function brickOwlName(part) {
    return BrickTools.getPartNameFromBOName(part.name || '', part.colorName || '');
  }

  function brickOwlMatches(part, tokens) {
    return matchesCollectionQuery([
      part.name, part.boid, part.type, part.colorName, part.colorId, part.condition,
      part.designIds, part.itemNumbers, brickOwlName(part)
    ], tokens);
  }

  function pabMatches(part, tokens) {
    return matchesCollectionQuery([
      part.partNumber, part.partName, part.partCategory, part.colorName, part.partColorId
    ], tokens);
  }

  function sortedBrickOwlParts(list) {
    const shared = BrickTools.PartsCollectionShared;
    return list.slice().sort(function (a, b) {
      return shared.compareValues(brickOwlName(a), brickOwlName(b), 1)
        || shared.compareValues(a.colorName, b.colorName, 1);
    });
  }

  function collapsePabParts(list) {
    const byPart = new Map();
    list.forEach(function (part) {
      const key = String(part.partNumber == null ? '' : part.partNumber) + '\u0000'
        + String(part.partColorId == null ? '' : part.partColorId);
      const existing = byPart.get(key);
      if (!existing) {
        byPart.set(key, Object.assign({}, part));
        return;
      }
      existing.quantity = (Number(existing.quantity) || 0) + (Number(part.quantity) || 0);
    });
    return Array.from(byPart.values());
  }

  function sortedPabParts(list) {
    const shared = BrickTools.PartsCollectionShared;
    return collapsePabParts(list).sort(function (a, b) {
      return shared.compareValues(a.partName, b.partName, 1)
        || shared.compareValues(a.partNumber, b.partNumber, 1)
        || shared.compareValues(a.colorName, b.colorName, 1);
    });
  }

  function splitRuleTokens(value) {
    return String(value == null ? '' : value)
      .split(';')
      .map(function (token) { return token.trim(); })
      .filter(function (token) { return token.length > 0; });
  }

  function tokenKey(token) {
    return String(token == null ? '' : token).toLowerCase();
  }

  function canonicalColorToken(token) {
    const lower = String(token || '').toLowerCase();
    if (lower === 'any') return 'Any';
    if (lower === 'other') return 'Other';
    if (lower === 'decorated') return 'Decorated';
    return String(token || '').trim();
  }

  function uniqueRuleTokens(value, canonicalize) {
    const seen = {};
    const result = [];
    splitRuleTokens(value).forEach(function (token) {
      const normalized = canonicalize ? canonicalize(token) : token;
      const key = tokenKey(normalized);
      if (!normalized || seen[key]) return;
      seen[key] = true;
      result.push(normalized);
    });
    return result;
  }

  BrickTools.CollectionUtils = {
    collectionTokens: collectionTokens,
    matchesCollectionQuery: matchesCollectionQuery,
    normalizeSizeText: normalizeSizeText,
    sizeDimensions: sizeDimensions,
    parseSizeQuery: parseSizeQuery,
    sizeMatches: sizeMatches,
    brickOwlMatches: brickOwlMatches,
    pabMatches: pabMatches,
    brickOwlName: brickOwlName,
    sortedBrickOwlParts: sortedBrickOwlParts,
    collapsePabParts: collapsePabParts,
    sortedPabParts: sortedPabParts,
    splitRuleTokens: splitRuleTokens,
    tokenKey: tokenKey,
    canonicalColorToken: canonicalColorToken,
    uniqueRuleTokens: uniqueRuleTokens
  };
})();
