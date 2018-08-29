# SquareTrade Global Jenkins Shared Library

This is the global Jenkins pipeline shared library for SquareTrade deployment pipelines.
This repository will be the home to all common pipeline deployment code used across all services.

# Jenkins Shared Library Description 

Taken from: https://jenkins.io/doc/book/pipeline/shared-libraries/

## Defining Shared Libraries

Defining Shared Libraries
A Shared Library is defined with a name, a source code retrieval method such as by SCM, and optionally a default version. The name should be a short identifier as it will be used in scripts.

The version could be anything understood by that SCM; for example, branches, tags, and commit hashes all work for Git. You may also declare whether scripts need to explicitly request that library (detailed below), or if it is present by default. Furthermore, if you specify a version in Jenkins configuration, you can block scripts from selecting a different version.

The best way to specify the SCM is using an SCM plugin which has been specifically updated to support a new API for checking out an arbitrary named version (Modern SCM option). As of this writing, the latest versions of the Git and Subversion plugins support this mode; others should follow.

If your SCM plugin has not been integrated, you may select Legacy SCM and pick anything offered. In this case, you need to include ${library.yourLibName.version} somewhere in the configuration of the SCM, so that during checkout the plugin will expand this variable to select the desired version. For example, for Subversion, you can set the Repository URL to https://svnserver/project/${library.yourLibName.version} and then use versions such as trunk or branches/dev or tags/1.0.



## Directory Structure

The directory structure of a Shared Library repository is as follows:

```
(root)
+- src                     # Groovy source files
|   +- com
|       +- squaretrade
|           +- Bar.groovy  # for org.foo.Bar class
+- vars
|   +- foo.groovy          # for global 'foo' variable
|   +- foo.txt             # help for 'foo' variable
+- resources               # resource files (external libraries only)
|   +- com 
|       +- squaretrade
|           +- bar.json    # static helper data for org.foo.Bar
```

The src directory should look like standard Java source directory structure. This directory is added to the classpath when executing Pipelines.

The vars directory hosts scripts that define global variables accessible from Pipeline. The basename of each *.groovy file should be a Groovy (~ Java) identifier, conventionally camelCased. The matching *.txt, if present, can contain documentation, processed through the system’s configured markup formatter (so may really be HTML, Markdown, etc., though the txt extension is required).

The Groovy source files in these directories get the same “CPS transformation” as in Scripted Pipeline.

A resources directory allows the libraryResource step to be used from an external library to load associated non-Groovy files. Currently this feature is not supported for internal libraries.

Other directories under the root are reserved for future enhancements.

## Global Shared Libraries

There are several places where Shared Libraries can be defined, depending on the use-case. Manage Jenkins » Configure System » Global Pipeline Libraries as many libraries as necessary can be configured.

Add a Global Pipeline Library

Since these libraries will be globally usable, any Pipeline in the system can utilize functionality implemented in these libraries.

These libraries are considered "trusted:" they can run any methods in Java, Groovy, Jenkins internal APIs, Jenkins plugins, or third-party libraries. This allows you to define libraries which encapsulate individually unsafe APIs in a higher-level wrapper safe for use from any Pipeline. Beware that anyone able to push commits to this SCM repository could obtain unlimited access to Jenkins. You need the Overall/RunScripts permission to configure these libraries (normally this will be granted to Jenkins administrators).
