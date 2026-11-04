// G1: Netflix out of the grid, the requested dock intact.
const fs = require('fs');
const xml = fs.readFileSync(process.argv[2] || 'work/tv-vibehack-source/launcher/app/src/main/res/values/config.xml', 'utf8');

const block = (name) => {
  const m = xml.match(new RegExp(`<string-array name="${name}">([\\s\\S]*?)</string-array>`));
  if (!m) return null;
  return [...m[1].matchAll(/<item>([^<]+)<\/item>/g)].map((x) => x[1].trim());
};

const dock = block('dock_apps');
const hidden = block('hidden_apps');
if (!dock || !hidden) { console.error('G1 config arrays missing'); process.exit(1); }

const problems = [];
if (!hidden.includes('com.netflix.ninja')) problems.push('netflix still visible');
if (dock.includes('com.netflix.ninja')) problems.push('netflix in dock');
if (dock.length !== 4) problems.push(`dock has ${dock.length} slots, layout supports 4`);
if (!dock.includes('net.oqee.androidtv.store')) problems.push('oqee left the dock');

if (problems.length) { console.error('G1 ' + problems.join(', ')); process.exit(1); }
console.log(`dock: ${dock.join(' ')}`);
console.log('hidden: ' + hidden.join(' '));
console.log('G1 dock+hidden OK');
