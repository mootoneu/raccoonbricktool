/**
 * Minimal browser stubs for loading the classic (non-module) browser scripts under Node's test runner.
 * Only the helpers exercised by the tests need to work; jQuery calls are stubbed as no-ops.
 */
const path = require('node:path');

global.window = global;

function fakeJQuery() {
  return {
    length: 0,
    on() { return this; },
    off() { return this; },
    empty() { return this; },
    append() { return this; },
    attr() { return this; },
    text() { return this; },
    html() { return this; },
    val() { return ''; },
    find() { return fakeJQuery(); },
    filter() { return fakeJQuery(); },
    prop() { return this; },
    removeClass() { return this; },
    addClass() { return this; },
    toggleClass() { return this; },
    trigger() { return this; }
  };
}

global.$ = Object.assign(function () { return fakeJQuery(); }, { fn: {} });
global.jQuery = global.$;
global.BrickTools = {};

function load(relativePath) {
  const file = path.join(__dirname, '..', '..', 'main', 'resources', 'static', 'js', relativePath);
  require(file);
  return global.BrickTools;
}

module.exports = { load };
