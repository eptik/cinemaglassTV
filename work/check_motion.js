// G2: motion reacts on the frame, not 380 ms later, and the row's focus scale is short.
const fs = require('fs');
const java = fs.readFileSync('work/tv-vibehack-source/launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java', 'utf8');

const problems = [];
const immediate = java.indexOf('retargetGlass(focusedCard);                 // move on the frame');
const delayed = java.indexOf('}, 380L);');
if (immediate < 0) problems.push('no immediate ring placement');
if (delayed < 0) problems.push('settle correction removed (ring will drift after dock peek)');
if (immediate > 0 && (delayed < immediate || delayed - immediate > 900)) {
  problems.push('immediate call is not next to the settle correction');
}
if (!/setDuration\(90\)\.start\(\);\s+\/\/ focus must land/.test(java)) {
  problems.push('row tiles do not react in 90 ms');
}
if (!/setDuration\(120\)|setDuration\(90\)/.test(java)) problems.push('row tile focus has no short animation');

if (problems.length) { console.error('G2 ' + problems.join(', ')); process.exit(1); }
console.log('G2 motion OK');
