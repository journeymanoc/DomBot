local internal = require('internal')
local util = require('util')
local exports = {}
local elementRenderQueue

-- Rendering phase control

exports.render = function()
    if type(elementRenderQueue) == 'table' then
        error('Rendering already started, invalid call to `gui.render()`.')
    end

    elementRenderQueue = {}
    _G.onRender()

    if elementRenderQueue ~= nil then
        internal.processElementRenderQueue(elementRenderQueue)
    end
end

exports.abortRendering = function()
    elementRenderQueue = nil
end


-- Element rendering

-- TODO: Consider adding a way to record elements to a user-specified buffer and then commit them to the render queue together
function renderElement(userContent, elementType, content, ...)
    assert(type(elementType) == 'string')
    assert(type(elementRenderQueue) == 'table', 'Rendering not started or it has been aborted, cannot render an element.')

    table.insert(elementRenderQueue, {
        type = elementType,
        content = util.mergeTables(content, util.validateArguments(userContent, ...)),
    })
end

exports.openGroup = function(args)
    renderElement(args,
        'group',
        { action = 'open' },
        { name = 'horizontal', type = 'boolean', required = false }
    )
end

exports.closeGroup = function(args)
    renderElement(args,
        'group',
        { action = 'close' }
    )
end

exports.renderText = function(args)
    renderElement(args,
        'text',
        {},
        { name = 'text', type = 'string' }
    )
end

exports.renderImage = function(args)
    renderElement(args,
        'image',
        {},
        { name = 'path', type = 'string' }
    )
end

exports.renderButton = function(args)
    renderElement(args,
        'button',
        {},
        { name = 'text', type = 'string' },
        { name = 'handler', type = 'function' }
    )
end


return exports
