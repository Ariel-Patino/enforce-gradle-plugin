/*
 * Copyright (c) Fundacion Jala. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for full license information.
 */

package org.fundacionjala.gradle.plugins.enforce.testselector

import groovy.json.JsonSlurper
import org.fundacionjala.gradle.plugins.enforce.tasks.salesforce.unittest.RunTestTaskConstants
import org.fundacionjala.gradle.plugins.enforce.utils.salesforce.MetadataComponents
import org.fundacionjala.gradle.plugins.enforce.utils.salesforce.runtesttask.CustomComponentTracker
import org.fundacionjala.gradle.plugins.enforce.wsc.rest.IArtifactGenerator

class TestSelectorByReference extends TestSelector  {

    private String srcPath
    private String filesParameterValue
    private IArtifactGenerator artifactGenerator
    private Map classAndTestMap = [:]
    private Boolean refreshClassAndTestMap = false
    private Boolean displayNoChangesMessage = false

    private final String APEX_CLASS_MEMBER_QUERY = 'SELECT FullName, ContentEntityId, SymbolTable FROM ApexClassMember WHERE MetadataContainerId = \'%1$s\''
    private final String CONTAINER_ASYNC_REQUEST_QUERY = 'SELECT State FROM ContainerAsyncRequest WHERE Id=\'%1$s\''
    private final String NO_RECENT_CHANGE_MESSAGE = 'You do not have any recent change to use to select Test classes.'

    /**
     * TestSelectorByReference class constructor
     * @param testClassNameList list of all available test class names
     * @param artifactGenerator instance reference of the current HttpAPIClient
     * @param filesParameterValue value provided by the user to filter the class names
     * @param refreshClassAndTestMap value provided by the user to specify refresh the class-test mapping
     */
    public TestSelectorByReference(String srcPath, ArrayList<String> testClassNameList, IArtifactGenerator artifactGenerator
                                   , String filesParameterValue, Boolean refreshClassAndTestMap) {
        super(testClassNameList)
        this.srcPath = srcPath
        this.artifactGenerator = artifactGenerator
        this.filesParameterValue = null
        if (filesParameterValue) {
            this.filesParameterValue = filesParameterValue.replace(".${MetadataComponents.CLASSES.getExtension()}", "")
            if (this.filesParameterValue == "*" || this.filesParameterValue == "all") {
                CustomComponentTracker customComponentTracker = new CustomComponentTracker(this.srcPath)
                this.filesParameterValue = (customComponentTracker.getFilesNameByExtension([MetadataComponents.CLASSES.getExtension()])).join("','")
                this.filesParameterValue = this.filesParameterValue.replace(".${MetadataComponents.CLASSES.getExtension()}", "")

                if (!this.filesParameterValue) {
                    displayNoChangesMessage = true
                }
            }
        }
        this.refreshClassAndTestMap = refreshClassAndTestMap
    }

    /**
     * Builds the class-test mapping
     */
    private void buildReferences() {
        JsonSlurper jsonSlurper = new JsonSlurper()
        if (this.refreshClassAndTestMap) {
            artifactGenerator.deleteContainer(RunTestTaskConstants.METADATA_CONTAINER_NAME)
        }
        Map containerResp = artifactGenerator.createContainer(RunTestTaskConstants.METADATA_CONTAINER_NAME)
        String containerId = containerResp["Id"]
        if (containerResp["isNew"]) {
            ArrayList<String> apexClassMemberId = []
            testClassNameList.collate(100).each {
                apexClassMemberId.addAll(artifactGenerator.createApexClassMember(containerId, it))
            }
            String containerAsyncRequestId = artifactGenerator.createContainerAsyncRequest(containerId)
            String requestStatus
            String requestStatusQuery = sprintf( CONTAINER_ASYNC_REQUEST_QUERY, [containerAsyncRequestId])
            while (requestStatus != 'Completed') {
                sleep(1000)
                requestStatus = jsonSlurper.parseText(artifactGenerator.executeQuery(requestStatusQuery)).records[0].State.toString()
            }
        }

        String apexClassMemberQuery = sprintf( APEX_CLASS_MEMBER_QUERY, [containerId[0..14]])
        jsonSlurper.parseText(artifactGenerator.executeQuery(apexClassMemberQuery)).records.each { classMember ->
            classMember.SymbolTable.each() { symbolTableResult ->
                symbolTableResult.every() { entry ->
                    if (entry.getKey() == "externalReferences") {
                        entry.getValue().each() {
                            if (it["namespace"] != "System") {
                                String classToAdd
                                if (it["namespace"]) {
                                    classToAdd = it["namespace"]
                                } else {
                                    classToAdd = it["name"]
                                }
                                if (!classAndTestMap.containsKey(classToAdd)) {
                                    classAndTestMap.put(classToAdd, new ArrayList<String>())
                                }
                                classAndTestMap.get(classToAdd).add(classMember.FullName)
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    ArrayList<String> getTestClassNames() {
        ArrayList<String> testClassList = new ArrayList<String>()
        if (logger && displayNoChangesMessage) {
            logger.error(NO_RECENT_CHANGE_MESSAGE)
        }

        if (this.filesParameterValue) {
            if (!classAndTestMap) {
                buildReferences()
            }
            classAndTestMap.keySet().each { className ->
                this.filesParameterValue.tokenize(RunTestTaskConstants.FILE_SEPARATOR_SIGN).each { wildCard ->
                    //if (className.contains(wildCard)) { //TODO: maybe we can work for wildCards at this point - if (contains("*") || startsWidth("*) endsWidth("*)) -> .replace("*", "")
                    if (className == wildCard ) {
                        testClassList.addAll(classAndTestMap.get(className))
                    }
                }
            }
        }

        return testClassList.unique()
    }
}
