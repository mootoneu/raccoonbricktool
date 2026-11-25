/** Flat ESLint configuration for the browser scripts (no bundler, classic scripts). */
module.exports = [
  {
    ignores: ['node_modules/**', 'target/**', 'src/test/js/**']
  },
  {
    files: ['src/main/resources/static/js/**/*.js'],
    languageOptions: {
      ecmaVersion: 2022,
      sourceType: 'script',
      globals: {
        window: 'readonly',
        document: 'readonly',
        console: 'readonly',
        $: 'readonly',
        jQuery: 'readonly',
        BrickTools: 'writable',
        BrickColor: 'readonly',
        Set: 'readonly',
        Map: 'readonly',
        Promise: 'readonly',
        Intl: 'readonly',
        URL: 'readonly',
        URLSearchParams: 'readonly',
        setTimeout: 'readonly',
        clearTimeout: 'readonly',
        setInterval: 'readonly',
        clearInterval: 'readonly',
        IntersectionObserver: 'readonly',
        d3: 'readonly',
        BrickSets: 'readonly'
      }
    },
    rules: {
      'no-undef': 'error',
      'no-unused-vars': 'warn',
      'no-dupe-keys': 'error',
      'no-constant-condition': 'error',
      'no-fallthrough': 'error'
    }
  }
];
