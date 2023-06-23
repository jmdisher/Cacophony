#!/bin/sh

xjc -no-header -p com.jeffdisher.cacophony.data.global.v2.root ../xsd/root2.xsd
xjc -no-header -p com.jeffdisher.cacophony.data.global.v2.recommendations ../xsd/recommendations2.xsd
xjc -no-header -p com.jeffdisher.cacophony.data.global.v2.description ../xsd/description2.xsd
xjc -no-header -p com.jeffdisher.cacophony.data.global.v2.record ../xsd/record2.xsd
xjc -no-header -p com.jeffdisher.cacophony.data.global.v2.records ../xsd/records2.xsd
xjc -no-header -p com.jeffdisher.cacophony.data.global.v2.extensions ../xsd/extensions2_video.xsd
