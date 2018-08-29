package com.squaretrade.deploy.model

class Tag {

    def steps
    List commitsSinceLastTag = null
    String tagType
    String mailAddr
    String printDetails
    String releaseName
    String branch
    String repoName
    String baseTag
    String firstTag
    String previousTag
    String latestTag
    String tagToCreate

    /**
     * @param tagType
     * @param mailAddr
     * @param steps
     * @param printDetails
     */
    Tag(String tagType, String mailAddr, def steps, Boolean printDetails = false){
        this.mailAddr = mailAddr
        this.tagType = tagType
        this.steps = steps
        this.printDetails = printDetails
    }

    /**
     * Build all needed properties to create the tag.
     */
    void buildProperties() {
        repoName = getRepoName()
        releaseName = getCurrentBranch().toLowerCase()

        baseTag = "${tagType}-${releaseName}-${repoName}"

        firstTag = getTag(this.baseTag, true)
        latestTag = getTag(this.baseTag, false)

        if (latestTag) {
            commitsSinceLastTag = getCommitLogs(latestTag, "HEAD")
        }

        if (commitsSinceLastTag == null || !commitsSinceLastTag.isEmpty()) {
            previousTag = latestTag ?: this.baseTag
            tagToCreate = incrementTagToCreate(previousTag)
        }
        else {
            throw new RuntimeException("Don't waste my time, no commits since last tag")
        }
    }

    /**
     * returns the oldest or latest tag based on the boolean
     *
     * @param baseTag
     * @return //tag
     */
    @SuppressWarnings("GrMethodMayBeStatic")
    String getTag(String baseTag, boolean oldest) {
        def tags = getFormattedTagList(baseTag)

        if (tags && oldest) {
            return tags.first()
        } else if (tags && !oldest) {
            return tags.last()
        }

        return null
    }

    /**
     * lists tags for a given tagType [rc/build/release]
     *
     * @param baseTag
     * @return
     */
    @SuppressWarnings("GrMethodMayBeStatic")
    List<String> getFormattedTagList(String baseTag) {
        String tagsList = executeSH("git tag --list")

        if (tagsList) {
            List arrayList = tagsList.split("\n")

            return arrayList.findAll { it.matches("${baseTag}-[0-9]+") }
        } else {
            return Collections.EMPTY_LIST
        }
    }

    /**
     * returns a list of commits between two tags
     *
     * @param startTag
     * @param endTag
     * @return
     */
    @SuppressWarnings("GrMethodMayBeStatic")
    List<String> getCommitLogs(String startTag, String endTag) {
        //--no-merges was removed from the command because it omits the merge commits from the log result
        String rawLog = executeSH("git log ${startTag}..${endTag} --oneline --pretty=tformat:%s-%an-%ad --date=iso")

        if(rawLog) {
            return rawLog.split("\n")
        } else {
            return Collections.EMPTY_LIST
        }
    }

    /**
     * Returns the current checked out branch.
     *
     * It uses substring Index because Jenkins/Cloudbees returns the branch name with 'ref/heads/...',
     * also this only works when 'localBranch' is set to true inside the pipeline (localBranch is required
     * if any git operation that needs the branch name is being used), the reason is that when GitSCM plugin performs the checkout
     * it gives you a 'Detached HEAD', and any git commands like 'git rev-parse --abbrev-ref HEAD' will return 'HEAD' as output.
     *
     * @return
     * @throws RuntimeException
     */
    String getCurrentBranch() {
        String branch = executeSH("git rev-parse --abbrev-ref HEAD").reverse()

        Integer firstIndex = branch.indexOf("/")
        Integer secondIndex = branch.indexOf("/", firstIndex + 1)

        branch = branch.substring(0, secondIndex).reverse()

        if (branch.startsWith("heads/")) {
            return branch.split("/").last()
        } else {
            return branch
        }
    }

    /**
     * Return the lowercase repo name based on the directory above .git.
     *
     * @return lowercase repository name
     */
    String getRepoName() {
        String repo = executeSH("git rev-parse --show-toplevel")

        return repo.trim().split("/").last().toLowerCase()
    }

    /**
     * Returns a message report with the diff between previous and latest tags
     * @param baseTag
     * @return
     */
    String getMailMessage(String baseTag) {
        def tags = getFormattedTagList(baseTag)
        int indx = tags.size() - 1
        Map<String, Map<String, List<String>>> diffs = new LinkedHashMap<>()

        for (; indx >= 1;) {
            Map<String, List<String>> logAgg = new HashMap<>()
            def diffKey = tags.get(indx - 1) + ".." + tags.get(indx)
            def startTag = tags.get(indx - 1)
            def endTag = tags.get(indx)

            List<String> logEntries = getCommitLogs(startTag, endTag)

            for (String entry : logEntries) {
                def key = entry.split(" ")[0].split(":").toString()

                if (logAgg.containsKey(key)) {
                    logAgg.get(key).add(entry)
                } else {
                    List entries = new ArrayList()
                    entries.add(entry)
                    logAgg.put(key, entries)
                }
            }

            diffs.put(diffKey, logAgg)

            indx--
        }

        StringBuffer diff = new StringBuffer()

        diffs.each { k, v ->
            if (v.size() > 0) {
                diff.append("\n")
                diff.append("\n")
                diff.append(">> $k \n")
                v.each { k1, v1 ->
                    diff.append("\t > $k1 \n")
                    v1.each { t ->
                        if (printDetails) {
                            diff.append("\t\t > $t \n")
                        }
                    }
                }
            }
        }

        return """<h2>Github Comparison</h2>
            Git tag created by job: <a href=${steps.env.BUILD_URL}>#${steps.env.BUILD_NUMBER}</a>
            <br/>
            <h3>Compare last and new $baseTag tags:</h3>
            https://github.squaretrade.com/${steps.params.GIT_REPO}/compare/$previousTag...$latestTag
            <br/>
            <h3>Compare first and new $baseTag tags:</h3>
            http://github.squaretrade.com/${steps.params.GIT_REPO}/compare/$firstTag...$latestTag
            <br/>
            <h3>Please find below the commits between various tags:</h3>
            <br/>
            <pre>${diff.toString()}</pre>"""
    }

    /**
     * Returns the job name
     * @param jobName
     * @return
     */
    static String getJobName(String jobName) {
        return jobName.split("/").last()
    }

    /**
     * performs the leg work to generate new tag to be created.
     *
     * Calculates the previous tags of a given branch and tag type.
     * validates if tag creation is needed and delegates the actual tag creation process.
     */
    String handleTagging() throws RuntimeException{
        if (!tagType) {
            throw new RuntimeException("Tag Type was not informed!")
        }

        createTag(tagToCreate)

        if(mailAddr) {
            sendMail("Tag ${latestTag} created for branch: ${releaseName}", getMailMessage(baseTag))
        }
        else {
            steps.echo "Email report disabled since no email address was informed..."
        }

        return latestTag
    }

    /**
     * Execute Jenkins SH step returning StdOut
     *
     * @param command
     * @return
     */
    String executeSH(String command) {
        return steps.sh(script: command, returnStdout: true).trim()
    }

    /**
     * Performs tag creation
     *
     * @param tagToCreate
     */
    void createTag(String tagToCreate) {
        steps.echo "creating new tag : ${tagToCreate}"
        latestTag = tagToCreate

        executeSH("git tag -m \"Tag createad in Cloudbees by job: ${getJobName(steps.env.JOB_NAME)}\" -a ${tagToCreate}")
        steps.echo("push tag ${tagToCreate}")

        executeSH("git push --tags")
        steps.echo("${tagToCreate} pushed ¯\\_(ツ)_/¯")
    }

    /**
     * Send the diff report email
     *
     * @param subject
     * @param body
     */
    void sendMail(String subject, String body) {
        steps.emailext(
                from: "cloudbees@squaretrade.com",
                to: "$mailAddr",
                subject: subject.trim(),
                body: body,
                mimeType: 'text/html')
    }

    /**
     * Increments the tag according to last tag commited.
     *
     * @param baseTag
     * @return
     */
    String incrementTagToCreate(String lastTag) {
        validateBeforeCreate(lastTag)

        String tag = lastTag.trim()

        if (lastTag == baseTag) {
            tag += "-01"

            firstTag = previousTag = tag

            return tag
        } else {
            Closure getVersionIndex = { tag.lastIndexOf('-') + 1 }

            def currentTagVersion = lastTag.substring(getVersionIndex())
            def newTagVersion = String.format("%02d", currentTagVersion.toInteger() + 1)

            return tag.substring(0, getVersionIndex()) + newTagVersion
        }
    }

    /**
     * Validates if the given tag type can be created
     *
     * @param baseTag
     */
    void validateBeforeCreate(String tag) throws RuntimeException {
        String baseTagToValidate = ''

        if (tag.startsWith("rc-")) {
            baseTagToValidate = "build-${releaseName}-${getRepoName()}"
        } else if (tag.startsWith("release-")) {
            baseTagToValidate = "rc-${releaseName}-${getRepoName()}"
        }

        def latestBuildTag = getTag(baseTagToValidate, false)

        if (latestBuildTag == null && !tag.startsWith("build-")) {
            throw new RuntimeException("Can't make a RELEASE tag without RC tag.\nCan't make a RC tag without BUILD tag.")
        }
    }
}