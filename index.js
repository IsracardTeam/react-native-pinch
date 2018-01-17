'use strict';

import { NativeModules } from 'react-native';

const { RNPinch } = NativeModules;

var Q = require('q');

module.exports =  {
    fetch: function (url, obj, callback) {
        var deferred = Q.defer();
        RNPinch.fetch(url, obj, (err, res) => {

            if (err) {
                deferred.reject(err);
            } else {
                res.json = function() {
                    return Q.fcall(function () {
                        return JSON.parse(res.bodyString);
                    });
                };
                res.text = function() {
                    return Q.fcall(function () {
                        return res.bodyString;
                    });
                };
                res.url = url;

                deferred.resolve(res);
            }

            deferred.promise.nodeify(callback);
        });
        return deferred.promise;
    }
    
};
