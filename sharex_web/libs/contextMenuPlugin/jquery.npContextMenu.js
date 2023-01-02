/*!
 * npContextMenu - v0.1.1 - 2018-10-18
 * https://github.com/alpenzoo/npContextMenu
 *
 * Copyright (c) 2018 Narcis Iulian Paun
 * Licensed under the MIT license.
 */

(function ($, window) {
	function computeMenuPosition(e, element) {
		var win = {
			"w":$(window)['width'](),
			"h":$(window)['height']()
			};
		var scroll = {
			"l":$(window)['scrollLeft'](),
			"t":$(window)['scrollTop']()
			};
		var menu = {
			"w":element['width'](),
			"h":element['height']()
			};
		var position = {
			"x":e.clientX + scroll.l,
			"y":e.clientY + scroll.t
		}
		// opening menu would pass on the outside of the page
		if (e.clientX + menu.w > win.w && menu.w < e.clientX) {
			position.x -= menu.w;
		}
		if (e.clientY + menu.h > win.h && menu.h < e.clientY) {
			position.y -= menu.h;
		}
		return {"x": position.x, "y": position.y};
	}
	function hideAllPopup() {
		$(".np-popup.active").trigger("npmenu:hide");
    }
    //close all popup elements on any document click
    $(document)
	.bind("click", hideAllPopup)
	.bind("contextmenu", hideAllPopup);

	$.fn.npContextMenu = function (options) {
		var settings = {
			setEvents: 'contextmenu click'
		};
		jQuery.extend( settings, options );

		var $menu = $(settings.menuSelector);
        if ($menu.length === 0) return;
		
		$menu.addClass("np-context-menu np-popup");
        // Open menu
        (function(element, settings){
			var fo = function(e){
                hideAllPopup();
				var myMenu = $(settings.menuSelector);
				var $originalTarget = $(e.target);
				
				var hideMe = function(){
                    myMenu
					.removeClass("active")
					.hide();
					if (settings.onMenuHide) {
						settings.onMenuHide.call(this, $originalTarget);
					}
				};
				if (settings.dynamicContent) {
					var content = settings.dynamicContent.call(this, $originalTarget);
					myMenu.empty().html(content);
				}
                //open menu
				var position = computeMenuPosition(e, myMenu);
				myMenu
                .show()
				.addClass("active")
                .css({
                    position: "absolute",
                    left: position.x,
                    top: position.y
                })
                .off('click')
                .on('click', 'a', function (e) {
					e.preventDefault();
					hideMe();
					if (settings.onMenuOptionSelected) {
						settings.onMenuOptionSelected.call(this, $originalTarget, $(e.target));
					}
                })
				.off("npmenu:hide")
				.on( "npmenu:hide", function( event ) {
					hideMe();
				});
                
				if (settings.onMenuShow) {
					settings.onMenuShow.call(this, $originalTarget);
				}
				return false;
			};
			element.bind(settings.setEvents, function (e) {
                if (e.ctrlKey) return; // cancel custom menu if CTRL is down
				fo(e);
				e.preventDefault();
				e.stopPropagation();
				//return false;
            });
        })($(this), settings);
		return this;
    };
	
	jQuery.fn.superMenu = function( options ) {
		var defaults = {
			textColor: "#000"
		};
		var settings = $.extend( {}, defaults, options );
		var originalTarget = null;
		
		(function(element, settings){
				var hideMe = function(){
                    element
					.removeClass("active")
					.hide();
					if (settings.onMenuHide) {
						settings.onMenuHide.call(this, originalTarget);
					}
				};
				var showMe = function(e){
					var position = computeMenuPosition(e, element);
					element
					.css({
						position: "absolute",
						left: position.x,
						top: position.y
					})
					.addClass("active")
					.show();
					if (settings.onMenuShow) {
						settings.onMenuShow.call(this, originalTarget);
					}
				};
				element
				.addClass("np-context-menu np-popup")
				.off("npmenu:hide")
				.on( "npmenu:hide", function( e ) {
					hideMe();
				})
				.off("npmenu:show")
				.on( "npmenu:show", function( e, oe ) {
					if (oe.ctrlKey) return;
					oe.preventDefault();
					oe.stopPropagation();
					hideAllPopup();
					originalTarget = oe.currentTarget;
					showMe(oe);
				})
                .off('click')
                .on('click', 'a', function (e) {
					e.preventDefault();
					hideMe();
					if (settings.onMenuOptionSelected) {
						settings.onMenuOptionSelected.call(this, originalTarget, e.target);
					}
                });
        })($(this), settings);
		return this;
	};
})(jQuery, window);
