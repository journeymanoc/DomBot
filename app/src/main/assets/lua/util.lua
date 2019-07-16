-- General utilities
local math = require('math')
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

exports.map = function(t, fn)
    local result = {}

    for k, v in pairs(t) do
        local newK, newV = fn(k, v)
        result[newK] = newV
    end

    return result
end

exports.filter = function(t, predicate)
    local result = {}

    for k, v in pairs(t) do
        if predicate(k, v) then
            table.insert(result, v)
        end
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

exports.Set = {}

exports.Set.new = function()
    local set = {}
    local private = {}

    function set:add(item)
        private[item] = true
    end

    function set:contains(item)
        return private[item]
    end

    function set:remove(item)
        local value = private[item]
        private[item] = nil
        return value
    end

    function set:len()
        return #private
    end

    function set:list()
        return exports.keys(private)
    end

    return set
end

exports.keys = function(table)
    local function closure(_, key)
        local new_key, _ = next(table, key)
        return new_key
    end

    return closure, table, nil
end

exports.values = function(table)
    local key

    local function closure(_, _)
        local new_key, new_value = next(table, key)
        key = new_key
        return new_value
    end

    return closure, table, nil
end

-- Merge sort implementation
local function merge(A, cmp, p, q, r)
	local n1 = q-p+1
	local n2 = r-q
	local left = {}
	local right = {}

	for i=1, n1 do
		left[i] = A[p+i-1]
	end
	for i=1, n2 do
		right[i] = A[q+i]
	end

	local i=1
	local j=1
  local k=p

	while i <= n1 and j <= n2 do
		if cmp(left[i], right[j]) <= 0 then
			A[k] = left[i]
			i=i+1
		else
			A[k] = right[j]
			j=j+1
		end
    k=k+1
	end

  while i <= n1 do
    A[k] = left[i]
    i=i+1
    k=k+1
  end

  while j <= n2 do
    A[k] = right[j]
    j=j+1
    k=k+1
  end
end

local function mergeSortInner(A, cmp, p, r)
	if p < r then
		local q = math.floor((p + r)/2)
		mergeSortInner(A, cmp, p, q)
		mergeSortInner(A, cmp, q+1, r)
		merge(A, cmp, p, q, r)
	end
end

--[[
 A stable sorting algorithm.
 @param `table` The table to shallow-copy and return the sorted version of
 @param `cmp`   The comparator to sort the values by, or `nil` for the default comparator usable for numbers
 ]]
exports.mergeSort = function(table, cmp)
    local result = exports.shallowCopy(table)
    local cmp = cmp or (function(a, b) return a - b end)

    mergeSortInner(result, cmp, 1, #result)

    return result
end

--[[
 A stable sorting algorithm.
 @param `table` The table to shallow-copy and return the sorted version of
 @param `cmp`   The comparator to sort the values by, or `nil` for the default comparator usable for numbers
 ]]
exports.sort = exports.mergeSort


return exports
