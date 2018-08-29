package com.squaretrade.deploy

class PipelineParameters {

    /**
     * List the SquareTrade pre-production environments for jobs that want to
     * @return
     */
    static final String preproductionEnvironments() {
        File file = new File(getClass().getResource('/com/squaretrade/preproductionEnvironments.txt').getFile())
        return file.text
    }

    static void main(String[] args) {
        println PipelineParameters.preproductionEnvironments()
    }
}
