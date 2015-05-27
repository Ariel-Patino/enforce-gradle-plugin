package org.fundacionjala.gradle.plugins.enforce.utils.salesforce

import com.sforce.soap.metadata.PackageTypeMembers
import org.fundacionjala.gradle.plugins.enforce.utils.Constants
import org.fundacionjala.gradle.plugins.enforce.utils.Util

import java.nio.file.Paths

class PackageCombiner {
    private static final ArrayList<String> SUB_COMPONENTS = ['CustomField', 'FieldSet', 'ValidationRule',
                                                             'CompactLayout', 'SharingReason']
    //RecordType
    //WebLink
    //SharingRecalculation
    //SearchLayout
    //ListView
    //HistoryRetentionPolicy
    //BusinessProcess
    //ActionOverride
    private static final String CUSTOM_OBJECT = 'CustomObject'
    private static final String ASTERISK = '*'

    /**
     * Combines package generated into build directory with package from project directory
     * @param projectPackagePath is a path of package from project directory
     * @param buildPackagePath is a path of package from build directory
     */
    public static void packageCombine(String projectPackagePath, String buildPackagePath) {
        if (new File(projectPackagePath).exists()) {
            PackageBuilder buildPackage = mergePackage(projectPackagePath, buildPackagePath)

            ArrayList<String> membersOfCustomField = new ArrayList<String>()
            ArrayList<String> membersOfCustomObject = new ArrayList<String>()

            buildPackage.metaPackage.types.each { PackageTypeMembers type ->
                if (SUB_COMPONENTS.contains(type.name)) {
                    membersOfCustomField = type.members as ArrayList<String>
                }
                if (type.name == CUSTOM_OBJECT) {
                    membersOfCustomObject = type.members as ArrayList<String>
                }
            }
            ArrayList<String> membersToRemove = getMembersToDelete(membersOfCustomField, membersOfCustomObject)
            buildPackage.removeMembers(CUSTOM_OBJECT, membersToRemove)
            writePackageCombined(buildPackage, buildPackagePath)
        }
    }

    /**
     * Removes components from package xml file
     * @param packagePath is package path
     * @param excludedFiles is an ArrayList with files name that were excluded
     */
    public static void removeMembersFromPackage(String packagePath, ArrayList<String> excludedFiles) {
        PackageBuilder packageBuilder = new PackageBuilder()
        FileReader packageFileReader = new FileReader(packagePath)
        packageBuilder.read(packageFileReader)
        Map<String, ArrayList<String>> componentToDelete = [:]

        excludedFiles.each {String fileName ->
            String componentType = getComponentType(fileName)
            String componentName = getComponentName(fileName)

            if (!componentToDelete.containsKey(componentType)){
                componentToDelete.put(componentType, [componentName])
            }

            if (componentToDelete.containsKey(componentType) &&
                    !componentToDelete.get(componentType).contains(componentName)) {
                componentToDelete.get(componentType).push(componentName)
            }
            String parentName = Util.getFirstPath(componentName)
            if(parentName != componentName) {
                componentToDelete.get(componentType).push(parentName)
            }
        }

        componentToDelete.each {String componentType, ArrayList<String> componentNames ->
            packageBuilder.removeMembers(componentType, componentNames)
        }

        FileWriter fileWriter = new FileWriter(packagePath)
        packageBuilder.write(fileWriter)
    }

    /**
     * Gets a component name without extension
     * @param fileName is component name
     * @return a component name
     */
    private static String getComponentName(String fileName) {
        String componentName = fileName
        if (fileName.contains(Constants.SLASH)) {
            componentName = fileName.substring( fileName.indexOf(Constants.SLASH) + 1, fileName.length())
        }
        if (!componentName.contains(Constants.SLASH)) {
            componentName = Util.getFileName(componentName)
        }
        return componentName
    }

    /**
     * Gets componentType
     * @param fileName is component name
     * @return component type
     */
    private static String getComponentType(String fileName) {
        String folderName = Util.getFirstPath(fileName)
        return MetadataComponents.getComponentByFolder(folderName).getTypeName()
    }

    /**
     * Joins two packages from build directory and project directory
     * @param projectPackagePath is path of package from project directory
     * @param buildPackagePath is path of package from build directory
     * @return a PackageBuilder object merged
     */
    private static PackageBuilder mergePackage(String projectPackagePath, String buildPackagePath) {
        PackageBuilder projectPackage = new PackageBuilder()
        PackageBuilder buildPackage = new PackageBuilder()

        projectPackage.read(new FileReader(projectPackagePath))
        buildPackage.read(new FileReader(buildPackagePath))

        projectPackage.metaPackage.types.each { PackageTypeMembers type ->
            if (!type.members.toList().contains(ASTERISK)) {
                buildPackage.update(type.name as String, type.members as ArrayList<String>)
            }
        }
        return buildPackage
    }

    /**
     * Gets objects that will be deleted
     * @param membersOfCustomField is a list of members of custom field
     * @param membersOfCustomObject is a list of members of custom object
     * @return an ArrayList with members that will delete
     */
    private static ArrayList<String> getMembersToDelete(ArrayList<String> membersOfCustomField, ArrayList<String> membersOfCustomObject) {
        ArrayList<String> membersToRemove = new ArrayList<String>()
        membersOfCustomField.each {String customFieldMember ->
            String objectName = customFieldMember.substring(0, customFieldMember.indexOf('.'))
            if (membersOfCustomObject.contains(objectName)) {
                membersToRemove.add(objectName)
            }
        }
        return membersToRemove
    }

    /**
     * Writes a package combined
     * @param packageCombined is a package combined
     * @param packageCombinedPath is path where packaged combined will be saved
     */
    private static void writePackageCombined(PackageBuilder packageCombined, String packageCombinedPath) {
        FileWriter fileWriter = new FileWriter(packageCombinedPath)
        packageCombined.write(fileWriter)
        fileWriter.close()
    }
}