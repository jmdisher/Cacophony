#!/bin/sh

xjc -p com.jeffdisher.cacophony.data.global.records ../xsd/global/records.xsd
xjc -p com.jeffdisher.cacophony.data.global.record ../xsd/global/record.xsd
xjc -p com.jeffdisher.cacophony.data.global.description ../xsd/global/description.xsd
xjc -p com.jeffdisher.cacophony.data.global.index ../xsd/global/index.xsd
xjc -p com.jeffdisher.cacophony.data.global.recommendations ../xsd/global/recommendations.xsd
