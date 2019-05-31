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
    assert(type(values) == 'table')

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


return exports
