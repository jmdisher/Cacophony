// This is a copy of "events_api.js" to emulate its API when running in the offline ("non-interactive") mode.
// NOTE:  This needs to be manually updated when every new key is added to the events_api.js implementation of the EVENTS_API object.

var EVENTS_API = {
	backgroundStatus: function(onCreate, onUpdate, onDelete, onSocketClose) { setTimeout(function() { onSocketClose( {reason:"offline mode"}); }) },
};

