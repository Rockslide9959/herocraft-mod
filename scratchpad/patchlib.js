const fs = require("fs");
exports.patch = function (p, pairs) {
  let s = fs.readFileSync(p, "utf8");
  const crlf = s.includes("\r\n");
  if (crlf) s = s.replace(/\r\n/g, "\n");
  for (const [a, b] of pairs) {
    if (!s.includes(a)) throw new Error(p + " missing: " + a.slice(0, 70));
    s = s.replace(a, () => b);
  }
  if (crlf) s = s.replace(/\n/g, "\r\n");
  fs.writeFileSync(p, s);
};
