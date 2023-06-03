// This page sets up the Angular global and registers common idioms.

// The GLOBAL_Application is the main exported symbol.
var GLOBAL_Application = angular.module('App', []);

// We put this user resolution helper here since it will later become part of the injected dependencies.
var GLOBAL_UnknownUserLoader = {
	map: {},
	// Returns a promise which resolve the tuple for the user (null if not found).  The tuple elements are documented in GET_UnknownUserInfo.java (except for publicKey which is injected here).
	// -name
	// -description
	// -userPicUrl
	// -email
	// -website
	// -publicKey
	loadTuple: function(publicKey)
	{
		return new Promise(resolve => {
			let tuple = this.map[publicKey];
			if (undefined === tuple)
			{
				// Load this from server.
				REST.GET("/server/unknownUser/" + publicKey)
					.then((response) => {
						if (!response.ok)
						{
							throw response.status;
						}
						return response.json();
					})
					.then((data) => {
						// We want to inject the public key, just for convenience.
						data["publicKey"] = publicKey;
						// Store this in the shared map.
						this.map[publicKey] = data;
						resolve(data);
					})
					// On error, just report null.
					.catch((errorCode) => resolve(null));
				
			}
			else
			{
				// Call in the next event loop iteration.
				window.setTimeout(function() { resolve(tuple); });
			}
		});
	},
};

// Define the factory methods for dependency injection.
// For UnknownUserLoader, we want to return a helper pointing to the shared instance since it has a cache.
GLOBAL_Application.factory('UnknownUserLoader', [function() { return function(publicKey) {return GLOBAL_UnknownUserLoader.loadTuple(publicKey); } ; }]);

let _template_channelSelector = ''
	+ '<div class="row btn-group">'
	+ '	<button class="btn btn-sm btn-danger dropdown-toggle" type="button" data-bs-toggle="dropdown" aria-expanded="false">{{selectedUserTitle}}</button>'
	+ '	<ul class="dropdown-menu">'
	+ '		<li ng-repeat="user in homeUserDescriptions"><a class="dropdown-item" ng-click="selectUser(user)">{{user.title}}</a></li>'
	+ '	</ul>'
	+ '</div>'
;
GLOBAL_Application.directive('cacoChannelSelector', [function()
{
	// NOTES:
	// -channelTuples - an array of objects as returned by GET_HomeChannels (has "name", "keyName", "publicKey", and "isSelected").
	// -onSelectUser - a method called with the public key of a user when it is selected by this widget.
	return {
		restrict: 'E',
		scope: {
			channelTuples: '=channelTuples',
			onSelectUser: '&onSelectUser',
		},
		replace: true,
		template: _template_channelSelector,
		link: function(scope, element, attrs)
		{
			let _setState = function(rawChannelTuples)
			{
				for (let user of rawChannelTuples)
				{
					// We want to create a readable description of a user so we will just add that to the structure.
					user["title"] = user["name"] + " (key name: " + user["keyName"] + ")";
					if (user["isSelected"])
					{
						scope.selectedUserTitle = user["title"];
					}
				}
				scope.homeUserDescriptions = rawChannelTuples;
			}
			
			scope.selectUser = function(userTuple)
			{
				scope.selectedUserTitle = userTuple["title"];
				scope.onSelectUser()(userTuple["publicKey"]);
			}
			
			scope.$watch('channelTuples', function(newValue, oldValue)
			{
				if (undefined !== newValue)
				{
					_setState(newValue);
				}
			});
		}
	}
}]);

let _template_userLink = '<a href="user.html?publicKey={{publicKey}}">{{name}}</a>';
GLOBAL_Application.directive('cacoUserLink', ['UnknownUserLoader', function(UnknownUserLoader)
{
	// NOTES:
	// -publicKey - the public key of an unknown user.
	return {
		restrict: 'E',
		scope: {
			publicKey: '=publicKey',
		},
		replace: true,
		template: _template_userLink,
		link: function(scope, element, attrs)
		{
			scope.name = 'Loading...';
			scope.$watch('publicKey', function(newValue, oldValue)
			{
				if (undefined !== newValue)
				{
					UnknownUserLoader(newValue).then((userTuple) => {
						scope.name = userTuple["name"];
						scope.$apply();
					});
				}
			});
		}
	}
}]);




