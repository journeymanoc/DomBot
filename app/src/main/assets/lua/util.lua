-- General utilities
local exports = {}

-- Merges tables, with values in leftmost tables prioritized
exports.mergeTables = function(...)
    local result = {}
    local varargs = table.pack(...)

    for i = varargs.n, 1, -1 do
        local table = varargs[i]

        for k, v in pairs(table) do
            result[k] = v
        end
    end

    return result
end

-- Filters arguments and makes sure they are provided and that they are of the correct type.
-- The first argument is the table to filter.
-- Then, argument descriptions follow, these can be either of:
-- a table: { name (string), type (string, default: nil), required (boolean, default: true) }
-- or just a string: name (string).
-- If some properties of the table are not provided, or a string is passed in instead of a table, the default values
-- are used for those arguments.
exports.validateArguments = function(values, ...)
    if values ~= nil then
        assert(type(values) == 'table')
    else
        values = {}
    end

    local result = {}
    local varargs = table.pack(...)

    for i = 1, varargs.n do
        local arg = varargs[i]
        local name
        local ty
        local required

        if type(arg) == 'string' then
            name = arg
            ty = nil
            required = nil
        elseif type(arg) == 'table' then
            assert(type(arg.name) == 'string')

            name = arg.name
            ty = arg.type
            required = arg.required
        else
            error('An argument description must either be a string or a table.')
        end

        -- Process `required`
        if required == nil then
            required = true
        end

        assert(type(required) == 'boolean')

        if required then
            assert(values[name] ~= nil, string.format("The argument `%s` is required and must not be `nil`.", name))
        end

        -- Process `type`
        if ty ~= nil and values[name] ~= nil then
            assert(type(ty) == 'string')
            assert(type(values[name]) == ty, string.format("Invalid type for argument `%s`: expected `%s`, received `%s`.", name, ty, type(values[name])))
        end

        result[name] = values[name]
    end

    return result
end

exports.map = function(table, fn)
    local result = {}

    for k, v in pairs(table) do
        local newK, newV = fn(k, v)
        result[newK] = newV
    end

    return result
end

exports.shallowCopy = function(table)
    return exports.map(table, function(k, v) return k, v end)
end

exports.Stack = {}

-- Create a Table with stack functions
exports.Stack.new = function(...)
    -- stack table
    local t = {}
    -- entry table
    local _et = {}

    -- push a value on to the stack
    function t:push(...)
        if ... then
            local targs = {...}
            -- add values
            for _,v in ipairs(targs) do
                table.insert(_et, v)
            end
        end
    end

    -- pop a value from the stack
    local function popOrPeek(num, preserve)
        -- get num values from stack
        local num = num or 1

        -- return table
        local entries = {}

        -- get values into entries
        for i = 1, num do
            -- get last entry
            if #_et ~= 0 then
                table.insert(entries, _et[#_et])
                -- remove last value
                if not preserve then
                    table.remove(_et)
                end
            else
                break
            end
        end
        -- return unpacked entries
        return table.unpack(entries)
    end

    function t:pop(num)
        return popOrPeek(num, false)
    end

    function t:peek(num)
        return popOrPeek(num, true)
    end

    -- get entries
    function t:len()
        return #_et
    end

    function t:list()
        return exports.shallowCopy(_et)
    end

    t:push(...)

    return t
end


return exports
