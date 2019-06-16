local internal = require('internal')
local exports = {}

exports.commit = function()
    internal.commitPersistentData()
end

return exports
