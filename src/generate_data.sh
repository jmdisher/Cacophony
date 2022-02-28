#!/bin/sh

xjc -no-header -p com.jeffdisher.cacophony.data.global.records ../xsd/global/records.xsd
xjc -no-header -p com.jeffdisher.cacophony.data.global.record ../xsd/global/record.xsd
xjc -no-header -p com.jeffdisher.cacophony.data.global.description ../xsd/global/description.xsd
xjc -no-header -p com.jeffdisher.cacophony.data.global.index ../xsd/global/index.xsd
xjc -no-header -p com.jeffdisher.cacophony.data.global.recommendations ../xsd/global/recommendations.xsd
