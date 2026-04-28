const fs = require('fs');

const jsCode = fs.readFileSync('base.js', 'utf8');

let startIndex = 0;
while ((startIndex = jsCode.indexOf('function(', startIndex)) !== -1) {
    let braceStart = jsCode.indexOf('{', startIndex);
    if (braceStart === -1) break;
    
    let depth = 1;
    let braceEnd = braceStart + 1;
    while (depth > 0 && braceEnd < jsCode.length) {
        if (jsCode[braceEnd] === '{') depth++;
        else if (jsCode[braceEnd] === '}') depth--;
        braceEnd++;
    }
    
    const body = jsCode.substring(braceStart, braceEnd);
    if (body.includes('.split("")') && body.includes('.join("")')) {
        // extract the name before function
        const before = jsCode.substring(Math.max(0, startIndex - 30), startIndex);
        const nameMatch = before.match(/([a-zA-Z0-9$_]+)\s*=?\s*$/);
        const name = nameMatch ? nameMatch[1] : 'unknown';
        
        console.log("Found:", name);
        console.log(body.substring(0, 100));
    }
    
    startIndex = braceEnd;
}
