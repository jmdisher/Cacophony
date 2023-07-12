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
				// We see undefined in cases where the binding isn't yet available but null in the cases where some kinds of placeholder data are plumbed through.
				if ((undefined !== newValue) && (null !== newValue))
				{
					UnknownUserLoader(newValue).then((userTuple) => {
						// If there was an error, we get a null.
						if (null !== userTuple)
						{
							scope.name = userTuple["name"];
						}
						else
						{
							scope.name = "Error loading";
						}
						scope.$apply();
					});
				}
			});
		}
	}
}]);

let _template_postMutable = ''
	+ '<div class="row card">'
	+ '<h5 class="card-header">{{postTuple.name}} (Posted by <caco-user-link public-key="postTuple.publisherKey"></caco-user-link> on {{postTuple.readableDate}})</h5>'
	+ '<div class="card-body container row">'
	+ '	<div class="col-md-3" ng-show="{{postTuple.cached}}">'
	+ '		<a href="play.html?elt={{postTuple.elementHash}}"><img class="img-fluid" ng-src="{{postTuple.thumbnailUrl}}" alt="{{postTuple.name}}"/></a>'
	+ '	</div>'
	+ '	<div class="col-md-3" ng-hide="{{postTuple.cached}}">'
	+ '		<a href="play.html?elt={{postTuple.elementHash}}">(not cached)</a>'
	+ '	</div>'
	+ '	<div class="col-md-9">'
	+ '		{{postTuple.description}}<br />'
	+ '		<div class="btn-group" role="group" ng-show="(undefined !== dangerName) || enableEdit">'
	+ '			<div class="btn-group" ng-show="undefined !== dangerName">'
	+ '				<button class="btn btn-sm btn-danger dropdown-toggle" type="button" data-bs-toggle="dropdown" aria-expanded="false" ng-disabled="isDangerActive">{{dangerName}}</button>'
	+ '				<ul class="dropdown-menu"><li><a class="dropdown-item" ng-click="onDanger()">Confirm</a></li></ul>'
	+ '			</div>'
	+ '			<a class="btn btn-small btn-warning" ng-show="enableEdit" ng-href="/basic_edit.html?elt={{postTuple.elementHash}}">Edit Post</a>'
	+ '		</div>'
	+ '	</div>'
	+ '</div>'
	+ '</div>'
;
GLOBAL_Application.directive('cacoPost', [function()
{
	// NOTES:
	return {
		restrict: 'E',
		scope: {
			postTuple: '=postTuple',
			enableEdit: '=enableEdit',
			dangerName: '@dangerName',
			onDangerKey: '&onDangerKey',
		},
		replace: true,
		template: _template_postMutable,
		link: function(scope, element, attrs)
		{
			scope.isDangerActive = false;
			
			scope.onDanger = function()
			{
				scope.isDangerActive = true;
				scope.onDangerKey()(scope.postTuple["elementHash"]);
			}
		}
	}
}]);

let _template_unknownUser = ''
	+ '<div class="card">'
	+ '	<h5 class="card-header">{{tuple.name}}</h5>'
	+ '	<div class="card-body row">'
	+ '		<div class="col-md-4">'
	+ '			<img class="img-fluid" ng-src="{{tuple.userPicUrl}}" />'
	+ '		</div>'
	+ '		<div class="col-md-8">'
	+ '			Public Key: <strong>{{tuple.publicKey}}</strong><br />'
	+ '			Description: <strong>{{tuple.description}}</strong><br />'
	+ '			Email: <span ng-show="{{null === tuple.email}}">(not provided)</span><span ng-show="{{null !== tuple.email}}"><a ng-href="mailto:{{tuple.email}}">{{tuple.email}}</a></span><br />'
	+ '			Website: <span ng-show="{{null === tuple.website}}">(not provided)</span><span ng-show="{{null !== tuple.website}}"><a ng-href="{{tuple.website}}">{{tuple.website}}</a></span><br />'
	+ '			<button class="btn btn-small btn-success" ng-click="onSuccess()" ng-show="isLoadComplete" ng-disabled="isSuccessActive">{{successName}}</button><br />'
	+ '		</div>'
	+ '	</div>'
	+ '</div>'
;
GLOBAL_Application.directive('cacoUnknownUser', ['UnknownUserLoader', function(UnknownUserLoader)
{
	// NOTES:
	return {
		restrict: 'E',
		scope: {
			publicKey: '=publicKey',
			successName: '@successName',
			onSuccessKey: '&onSuccessKey',
		},
		replace: true,
		template: _template_unknownUser,
		link: function(scope, element, attrs)
		{
			scope.onSuccess = function()
			{
				scope.isSuccessActive = true;
				scope.onSuccessKey()(scope.publicKey);
			}
			
			scope.$watch('publicKey', function(newValue, oldValue)
			{
				if ((undefined !== newValue) && (null !== newValue))
				{
					// Clear state whenever this changes.
					scope.isSuccessActive = false;
					scope.isLoadComplete = false;
					scope.tuple = {
						name: "Loading...",
						description: "Loading...",
						userPicUrl: null,
						email: null,
						website: null,
					};
					UnknownUserLoader(newValue).then((userTuple) => {
						// We get null on error.
						if (null !== userTuple)
						{
							scope.isLoadComplete = true;
							scope.tuple = userTuple;
						}
						else
						{
							scope.tuple = {
								name: "Error loading",
								description: "Error loading: " + scope.publicKey,
								userPicUrl: null,
								email: null,
								website: null,
							};
						}
						scope.$apply();
					});
				}
			});
		}
	}
}]);

