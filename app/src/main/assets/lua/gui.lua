local internal = require('internal')
local util = require('util')
local exports = {}
local elementRenderQueueStack

-- Rendering phase control

exports.isRendering = function()
    return elementRenderQueueStack ~= nil
end

local function assertRendering()
    assert(exports.isRendering(), 'Rendering not started or it has been aborted, cannot render an element.')
end

local function assertNotRendering(functionName)
    assert(not exports.isRendering(), 'Function `gui.'..functionName..'` may not be called while rendering is already in progress.')
end

exports.render = function(rawArgs)
    assertNotRendering('render')

    local args = util.validateArguments(rawArgs,
        { name = 'resetScroll', type = 'boolean', required = false }
    )

    elementRenderQueueStack = util.Stack.new({})
    _G.onRender()

    -- process render queue if rendering has not been aborted
    if exports.isRendering() then
        internal.processElementRenderQueue(args, elementRenderQueueStack:list()[1])
    end

    elementRenderQueueStack = nil
end

exports.abortRendering = function()
    assertRendering()
    elementRenderQueueStack = nil
end

exports.updateElement = function(rawArgs)
    assertNotRendering('updateElement')

    local args = util.validateArguments(rawArgs,
        { name = 'id',   type = 'string' },
        { name = 'data', type = 'table'  }
    )

    return internal.updateElement(args)
end


-- Element rendering

-- TODO: Consider adding a way to record elements to a user-specified buffer and then commit them to the render queue together
function renderElement(userContent, elementType, content, ...)
    assert(type(elementType) == 'string')
    assertRendering()

    local elementRenderQueue = elementRenderQueueStack.peek(elementRenderQueueStack, 1)
    local element = {
        type = elementType,
        content = util.mergeTables(content, util.validateArguments(userContent, ...)),
    }

    table.insert(elementRenderQueue, element)

    return element
end

exports.openGroup = function(args)
    local element = renderElement(args,
        'group',
        { children = {} },
        { name = 'id',         type = 'string',   required = false },
        { name = 'enabled',    type = 'boolean',  required = false }, -- `false`, if interaction should be disabled; `true` by default
        { name = 'opacity',    type = 'number',   required = false }, -- 0.0 = fully transparent; 1.0 = fully opaque
        { name = 'gravity',                       required = false }, -- may be either a string or a table of strings
        { name = 'width',                         required = false }, -- an integer, 'matchParent' or 'wrapContent'
        { name = 'height',                        required = false }, -- an integer, 'matchParent' or 'wrapContent'
        { name = 'margin',                        required = false }, -- an integer or a table of integers with optional values: horizontal, vertical, start, top, end, bottom
        { name = 'padding',                       required = false }, -- an integer or a table of integers with optional values: horizontal, vertical, start, top, end, bottom
        { name = 'background', type = 'number',   required = false }, -- the background color in the format 0xAARRGGBB
        { name = 'weight',     type = 'number',   required = false }, -- a floating point number
        { name = 'horizontal', type = 'boolean',  required = false },
        { name = 'handler',    type = 'function', required = false }
    )
    elementRenderQueueStack:push(element.content.children)
end

exports.closeGroup = function(_)
    assertRendering()
    assert(elementRenderQueueStack:len() > 1, 'Cannot close group, since no group is currently open.')
    elementRenderQueueStack:pop()
end

exports.renderText = function(args)
    renderElement(args,
        'text',
        {},
        { name = 'id',         type = 'string',   required = false },
        { name = 'enabled',    type = 'boolean',  required = false }, -- `false`, if interaction should be disabled; `true` by default
        { name = 'opacity',    type = 'number',   required = false }, -- 0.0 = fully transparent; 1.0 = fully opaque
        { name = 'gravity',                       required = false }, -- may be either a string or a table of strings
        { name = 'width',                         required = false }, -- an integer, 'matchParent' or 'wrapContent'
        { name = 'height',                        required = false }, -- an integer, 'matchParent' or 'wrapContent'
        { name = 'weight',                        required = false }, -- a floating point number
        { name = 'margin',                        required = false }, -- an integer or a table of integers with optional values: horizontal, vertical, start, top, end, bottom
        { name = 'padding',                       required = false }, -- an integer or a table of integers with optional values: horizontal, vertical, start, top, end, bottom
        { name = 'background', type = 'number',   required = false }, -- the background color in the format 0xAARRGGBB
        { name = 'text',       type = 'string',   required = false },
        { name = 'subtext',    type = 'string',   required = false },
        { name = 'handler',    type = 'function', required = false }
    )
end

exports.renderImage = function(args)
    renderElement(args,
        'image',
        {},
        { name = 'id',         type = 'string',   required = false },
        { name = 'enabled',    type = 'boolean',  required = false }, -- `false`, if interaction should be disabled; `true` by default
        { name = 'opacity',    type = 'number',   required = false }, -- 0.0 = fully transparent; 1.0 = fully opaque
        { name = 'width',                         required = false }, -- an integer, 'matchParent' or 'wrapContent'
        { name = 'height',                        required = false }, -- an integer, 'matchParent' or 'wrapContent'
        { name = 'weight',                        required = false }, -- a floating point number
        { name = 'margin',                        required = false }, -- an integer or a table of integers with optional values: horizontal, vertical, start, top, end, bottom
        { name = 'padding',                       required = false }, -- an integer or a table of integers with optional values: horizontal, vertical, start, top, end, bottom
        { name = 'background', type = 'number',   required = false }, -- the background color in the format 0xAARRGGBB
        { name = 'path',       type = 'string',                    },
        { name = 'handler',    type = 'function', required = false }
    )
end

exports.renderButton = function(args)
    renderElement(args,
        'button',
        {},
        { name = 'id',         type = 'string',   required = false },
        { name = 'enabled',    type = 'boolean',  required = false }, -- `false`, if interaction should be disabled; `true` by default
        { name = 'opacity',    type = 'number',   required = false }, -- 0.0 = fully transparent; 1.0 = fully opaque
        { name = 'gravity',                       required = false }, -- may be either a string or a table of strings
        { name = 'width',                         required = false }, -- an integer, 'matchParent' or 'wrapContent'
        { name = 'height',                        required = false }, -- an integer, 'matchParent' or 'wrapContent'
        { name = 'weight',                        required = false }, -- a floating point number
        { name = 'margin',                        required = false }, -- an integer or a table of integers with optional values: horizontal, vertical, start, top, end, bottom
        { name = 'padding',                       required = false }, -- an integer or a table of integers with optional values: horizontal, vertical, start, top, end, bottom
        { name = 'background', type = 'number',   required = false }, -- the background color in the format 0xAARRGGBB
        { name = 'text',       type = 'string',                    },
        { name = 'handler',    type = 'function', required = false }
    )
end

exports.renderCheckBox = function(args)
    renderElement(args,
        'checkBox',
        {},
        { name = 'id',         type = 'string',   required = false },
        { name = 'enabled',    type = 'boolean',  required = false }, -- `false`, if interaction should be disabled; `true` by default
        { name = 'opacity',    type = 'number',   required = false }, -- 0.0 = fully transparent; 1.0 = fully opaque
        { name = 'gravity',                       required = false }, -- may be either a string or a table of strings
        { name = 'width',                         required = false }, -- an integer, 'matchParent' or 'wrapContent'
        { name = 'height',                        required = false }, -- an integer, 'matchParent' or 'wrapContent'
        { name = 'weight',                        required = false }, -- a floating point number
        { name = 'margin',                        required = false }, -- an integer or a table of integers with optional values: horizontal, vertical, start, top, end, bottom
        { name = 'padding',                       required = false }, -- an integer or a table of integers with optional values: horizontal, vertical, start, top, end, bottom
        { name = 'background', type = 'number',   required = false }, -- the background color in the format 0xAARRGGBB
        { name = 'text',       type = 'string',   required = false },
        { name = 'checked',    type = 'boolean',  required = false },
        { name = 'handler',    type = 'function', required = false }
    )
end

exports.renderTextInput = function(args)
    renderElement(args,
        'textInput',
        {},
        { name = 'id',          type = 'string',   required = false },
        { name = 'enabled',     type = 'boolean',  required = false }, -- `false`, if interaction should be disabled; `true` by default
        { name = 'opacity',     type = 'number',   required = false }, -- 0.0 = fully transparent; 1.0 = fully opaque
        { name = 'gravity',                        required = false }, -- may be either a string or a table of strings
        { name = 'width',                          required = false }, -- an integer, 'matchParent' or 'wrapContent'
        { name = 'height',                         required = false }, -- an integer, 'matchParent' or 'wrapContent'
        { name = 'weight',                         required = false }, -- a floating point number
        { name = 'margin',                         required = false }, -- an integer or a table of integers with optional values: horizontal, vertical, start, top, end, bottom
        { name = 'padding',                        required = false }, -- an integer or a table of integers with optional values: horizontal, vertical, start, top, end, bottom
        { name = 'background',  type = 'number',   required = false }, -- the background color in the format 0xAARRGGBB
        { name = 'text',        type = 'string',   required = false },
        { name = 'placeholder', type = 'string',   required = false },
        { name = 'inputType',                      required = false },
        { name = 'handler',     type = 'function', required = false }
    )
end

exports.renderNumberPicker = function(args)
    renderElement(args,
        'numberPicker',
        {},
        { name = 'id',         type = 'string',   required = false },
        { name = 'enabled',    type = 'boolean',  required = false }, -- `false`, if interaction should be disabled; `true` by default
        { name = 'opacity',    type = 'number',   required = false }, -- 0.0 = fully transparent; 1.0 = fully opaque
        { name = 'gravity',                       required = false }, -- may be either a string or a table of strings
        { name = 'width',                         required = false }, -- an integer, 'matchParent' or 'wrapContent'
        { name = 'height',                        required = false }, -- an integer, 'matchParent' or 'wrapContent'
        { name = 'weight',                        required = false }, -- a floating point number
        { name = 'margin',                        required = false }, -- an integer or a table of integers with optional values: horizontal, vertical, start, top, end, bottom
        { name = 'padding',                       required = false }, -- an integer or a table of integers with optional values: horizontal, vertical, start, top, end, bottom
        { name = 'background', type = 'number',   required = false }, -- the background color in the format 0xAARRGGBB
        { name = 'value',      type = 'number',   required = false }, -- the default integer value
        { name = 'minValue',   type = 'number',   required = false }, -- the minimum selectable integer value
        { name = 'maxValue',   type = 'number',   required = false }, -- the maximum selectable integer value
        { name = 'wrap',       type = 'boolean',  required = false }, -- true, if the values should wrap around
        { name = 'formatter',  type = 'function', required = false }, -- a function that takes an integer value and formats it as a string to display as an item
        { name = 'handler',    type = 'function', required = false }  -- called when the selected value changed, with the new value as an argument
    )
end


return exports
