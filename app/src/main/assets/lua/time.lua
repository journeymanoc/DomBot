local internal = require('internal')
local util = require('util')
local exports = {}

exports.getCurrentInstant = function()
    return internal.getCurrentInstant()
end

exports.compareInstants = function(a, b)
    local fieldsToOrderBy = {
        'year', 'monthOfYear', 'dayOfMonth', 'hourOfDay', 'minuteOfHour', 'secondOfMinute', 'millisecondOfSecond'
    }
    for field in fieldsToOrderBy do
        local difference = a[field] - b[field]

        if difference ~= 0 then
            if difference < 0 then
                return -1
            else
                return 1
            end
        end
    end

    return 0
end

exports.addDurationToInstant = function(duration, instant)
    return internal.addDurationToInstant(duration, instant)
end

exports.scheduleNotify = function(rawArgs)
    local args = util.validateArguments(rawArgs,
        { name = 'instant', type = 'table' }, -- the instant to run the notification on (or later, if app is inactive)
        { name = 'data', required = false } -- additional custom data to pass as an argument to `onNotify`
    )
    internal.scheduleNotify(args.instant, args.data)
end

exports.delayNotify = function(rawArgs)
    local args = util.validateArguments(rawArgs,
        { name = 'duration', type = 'table' }, -- the duration, eg.: `{ seconds = 20, minutes = 1 }`
        { name = 'data', required = false } -- additional custom data to pass as an argument to `onNotify`
    )
    local instant = exports.addDurationToInstant(args.duration, exports.getCurrentInstant())
    exports.scheduleNotify({
        instant = instant,
        data = args.data,
    })
end

return exports
