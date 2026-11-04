// G3: the Nuvio row is wired to real session data, and its resolver classifies both ways.
const fs = require('fs');
const path = 'work/tv-vibehack-source/launcher/app/src/main/java/com/gathin/tvhome/';
const hist = fs.readFileSync(path + 'NuvioHistory.java', 'utf8');
const np = fs.readFileSync(path + 'NowPlaying.java', 'utf8');
const main = fs.readFileSync(path + 'MainActivity.java', 'utf8');
const xml = fs.readFileSync('work/tv-vibehack-source/launcher/app/src/main/res/layout/activity_main.xml', 'utf8');

const problems = [];
if (!/class NuvioHistory/.test(hist)) problems.push('history store missing');
if (!/v3-cinemeta\.strem\.io/.test(hist)) problems.push('no id resolver');
if (!/nuvio:\/\/detail\//.test(main)) problems.push('no deep link');
if (!/NuvioHistory\.observe\(/.test(np)) problems.push('sessions are not captured');
if (!/com\.nuvio\.tv/.test(np)) problems.push('capture is not scoped to Nuvio');
if (!/id="@\+id\/nuvioRow"/.test(xml)) problems.push('row not in layout');
if (!/setVisibility\(View\.GONE\)/.test(main)) problems.push('empty row is not hidden');
if (problems.length) { console.error('G3 ' + problems.join(', ')); process.exit(1); }

// Live oracle: the same endpoint the app uses. Positive and negative control.
const lookup = async (title, type) => {
  const url = `https://v3-cinemeta.strem.io/catalog/${type}/top/search=${encodeURIComponent(title)}.json`;
  const res = await fetch(url, { signal: AbortSignal.timeout(8000) });
  if (!res.ok) throw new Error(`${res.status} from cinemeta`);
  const json = await res.json();
  return (json.metas || []).map((m) => m.id).filter((id) => id.startsWith('tt'));
};

(async () => {
  const positive = await lookup('The Wire', 'series');
  const negative = await lookup('zzqqxx not a real title 9931', 'movie');
  if (!positive.length) { console.error('G3 resolver found no id for a real show'); process.exit(1); }
  if (negative.length) { console.error('G3 resolver invents ids for nonsense titles'); process.exit(1); }
  console.log(`resolved The Wire -> ${positive[0]}, nonsense -> none`);
  console.log('G3 nuvio OK');
})().catch((e) => { console.error('G3 resolver check failed: ' + e.message); process.exit(1); });
