// This page sets up the Angular global and registers common idioms.

// The GLOBAL_Application is the main exported symbol.
var GLOBAL_Application = angular.module('App', []);

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
