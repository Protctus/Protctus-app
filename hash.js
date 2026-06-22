const crypto = require("crypto");
const password = "wjegfjer[pytu34-@124";
const hash = crypto.createHash("sha256").update(password).digest("hex");
console.log(hash);
