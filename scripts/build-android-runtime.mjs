import { build } from 'esbuild';

await build({
  entryPoints: ['src/android-runtime/index.ts'],
  bundle: true,
  format: 'iife',
  platform: 'browser',
  outfile: 'android/app/build/generated/assets/webRuntime/name-replacer.js',
  minify: true
});
