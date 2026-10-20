package pt.unl.fct.di.syspriv.util;

import org.apache.commons.math3.stat.regression.SimpleRegression;

public class LinearRegression {

    public class RegressionResult {
        private double slope;
        private double intercept;
        private double rSquare;
        private double meanSquareError;

        public RegressionResult() {};
        public RegressionResult(
                        double slope,
                        double intercept,
                        double rSquare,
                        double meanSquareError
                        ){
           this.slope = slope;
           this.intercept = intercept;
           this.rSquare = rSquare;
           this.meanSquareError = meanSquareError;
        }    
        /**
         * Get slope.
         *
         * @return slope as double.
         */
        public double getSlope()
        {
            return slope;
        }
        
        /**
         * Set slope.
         *
         * @param slope the value to set.
         */
        public void setSlope(double slope)
        {
            this.slope = slope;
        }
        
        /**
         * Get intercept.
         *
         * @return intercept as double.
         */
        public double getIntercept()
        {
            return intercept;
        }
        
        /**
         * Set intercept.
         *
         * @param intercept the value to set.
         */
        public void setIntercept(double intercept)
        {
            this.intercept = intercept;
        }
        
        /**
         * Get rSquare.
         *
         * @return rSquare as double.
         */
        public double getRSquare()
        {
            return rSquare;
        }
        
        /**
         * Set rSquare.
         *
         * @param rSquare the value to set.
         */
        public void setRSquare(double rSquare)
        {
            this.rSquare = rSquare;
        }
        
        /**
         * Get meanSquareError.
         *
         * @return meanSquareError as double.
         */
        public double getMeanSquareError()
        {
            return meanSquareError;
        }
        
        /**
         * Set meanSquareError.
         *
         * @param meanSquareError the value to set.
         */
        public void setMeanSquareError(double meanSquareError)
        {
            this.meanSquareError = meanSquareError;
        }
    }
    
    public RegressionResult run(double[] age, double[] glucose) {
        assert(age.length == glucose.length);
        SimpleRegression reg = new SimpleRegression();
        for (int i = 0; i < age.length; i++)
            reg.addData(age[i], glucose[i]);
        RegressionResult res = new RegressionResult(reg.getSlope(),
                        reg.getIntercept(),
                        reg.getRSquare(),
                        reg.getMeanSquareError());
        return res;
    }
        
}
