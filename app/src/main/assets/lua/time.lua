local internal = require('internal')
local util = require('util')
local exports = {}

--[[
 A method to get the current date and time -- an instant. The returned value is a table, which contains the following
 two sets of fields with the specified bounds. The first set uniquely identifies this instant:
 * `millisecondOfSecond` -- ℤ ∩ <0; 1000)
 * `secondOfMinute` -- ℤ ∩ <0; 60)
 * `minuteOfHour` -- ℤ ∩ <0; 60)
 * `hourOfDay` -- ℤ ∩ <0; 24)
 * `dayOfMonth` -- ℤ ∩ <1; +∞)
 * `monthOfYear` -- ℤ ∩ <1; 12>
 * `year` -- ℤ ∩ <0; +∞)
 The second set is not used for any computations and is provided as a convenience by functions returning instants:
 * `hourOfMorningOrAfternoon` -- ℤ ∩ <0; 12)
 * `morningOrAfternoonOfDay` -- {0, 1}
 * `dayOfWeek` -- ℤ ∩ <0; 7)
 * `dayOfWeekInMonth` -- Incomprehensible, look up the docs for `java.util.Calendar#DAY_OF_WEEK_IN_MONTH`
 * `dayOfYear` -- ℤ ∩ <1; 365> or ℤ ∩ <1; 366> on leap years
 * `weekOfMonth` -- ℤ ∩ <1; +∞)
 * `weekOfYear` -- ℤ ∩ <1; +∞)

 Look up `java.util.Calendar#DAY_OF_WEEK_IN_MONTH` for closer details of these fields. Note, that the `monthOfYear` has
 a different range.

 @return The current instant (date, time)

 @see `compareInstants`
 @see `addDurationToInstant`
 ]]
exports.getCurrentInstant = function()
    return internal.getCurrentInstant()
end

--[[
 @param `a` The first instant;
 @param `b` The second instant;
 @return `-1`, if `a` happens before `b`; `0`, if these instants are considered the same; `1`, if `a` happens after `b`

 @see `getCurrentInstant`
 @see `addDurationToInstant`
]]
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

--[[
 Computes the duration it takes to shift instant `a` to instant `b`. If `a` happens after `b`, then the result is
 negative.
 ]]
exports.getDurationBetween = function(a, b)
    return {
        milliseconds = b.millisecondOfSecond - a.millisecondOfSecond,
        seconds = b.secondOfMinute - a.secondOfMinute,
        minutes = b.minuteOfHour - a.minuteOfHour,
        hours = b.hourOfDay - a.hourOfDay,
        days = b.dayOfMonth - a.dayOfMonth,
        months = b.monthOfYear - a.monthOfYear,
        years = b.year - a.year
    }
end

--[[
 @param `duration` The duration to move the specified `instant` by, specified as a table with fields of signed integers:
                   `milliseconds`, `seconds`, `minutes`, `hours`, `days`, `months`, `years`;
 @param `instant`  The instant (date, time) to add the `duration` to
 @return The `instant` shifted by `duration`

 @see `compareInstants`
 @see `getCurrentInstant`
]]
exports.addDurationToInstant = function(duration, instant)
    return internal.addDurationToInstant(duration, instant)
end

--[[
 Schedules a call to the global function `onNotify` to be called at `rawArgs.instant` with the notification as a
 parameter (a table containing the fields `id`, `instant` and `data`)

 @param `rawArgs`         A table with the following fields:
 @param `rawArgs.id`      The optional identificator of the notification, used to cancel a scheduled notification or
                          check whether a notification with this ID is scheduled;
 @param `rawArgs.instant` The instant (date, time) to run the notification at;
 @param `rawArgs.data`    Additional data to pass to the global notification handler `onNotify`;
 @return The previously scheduled notification with the same `id` which was cancelled by this call (a table with the
         fields `id`, `instant` and `data`), or `nil` if no such notification was scheduled

 @see `scheduleNotificationAfter`
 @see `getCurrentInstant`
 @see `addDurationToInstant`
 ]]
exports.scheduleNotificationAt = function(rawArgs)
    local args = util.validateArguments(rawArgs,
        { name = 'id', type = 'string', required = false },
        { name = 'instant', type = 'table' }, -- the instant to run the notification on (or later, if app is inactive)
        { name = 'data', required = false } -- additional custom data to pass as an argument to `onNotify`
    )
    return internal.scheduleNotificationAt(args.id, args.instant, args.data)
end

--[[
 Schedules a call to the global function `onNotify` to be called after `rawArgs.duration` with the notification as a
 parameter (a table containing the fields `id`, `instant` and `data`)

 @param `rawArgs`          A table with the following fields:
 @param `rawArgs.id`       The optional identificator of the notification, used to cancel a scheduled notification or
                           check whether a notification with this ID is scheduled;
 @param `rawArgs.duration` The duration to wait before running this notification, specified as a table with integer
                           fields: `milliseconds`, `seconds`, `minutes`, `hours`, `days`, `months`, `years`;
 @param `rawArgs.data`     Additional data to pass to the global notification handler `onNotify`;
 @return The previously scheduled notification with the same `id` which was cancelled by this call (a table with the
         fields `id`, `instant` and `data`), or `nil` if no such notification was scheduled

 @see `scheduleNotificationAt`
 @see `addDurationToInstant`
 ]]
exports.scheduleNotificationAfter = function(rawArgs)
    local args = util.validateArguments(rawArgs,
        { name = 'id', type = 'string', required = false },
        { name = 'duration', type = 'table' }, -- the duration, eg.: `{ seconds = 20, minutes = 1 }`
        { name = 'data', required = false } -- additional custom data to pass as an argument to `onNotify`
    )
    local instant = exports.addDurationToInstant(args.duration, exports.getCurrentInstant())
    return exports.scheduleNotificationAt({
        id = args.id,
        instant = instant,
        data = args.data,
    })
end

exports.getNotification = function(id)
    return internal.getNotification(id)
end

exports.isNotificationScheduled = function(id)
    if exports.getNotification(id) then
        return true
    else
        return false
    end
end

exports.cancelNotification = function(id)
    return internal.cancelNotification(id)
end

return exports
