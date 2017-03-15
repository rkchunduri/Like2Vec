import pandas as pd
import seaborn as sb
import matplotlib.pyplot as plt
import sys
from scipy.stats import anderson

if __name__ == "__main__":

    def plotDist(filename,title,output):
        """
        generates plot of the distribution of the l2 norms of embeddings
        
        Parameters
        ----------
        filename: String; location of embeddings
        title: String; parial title for plot
        output: location where you want the plot saved
        
        Return
        ------
        None; prints a plot of the distribution of the l2 norms of embeddings
        """
        df = pd.read_table(filename,names = "d")
        df["d"] = df["d"].apply(lambda x: float(x.replace(")","").replace("(","").split(",")[1]))
        plt.figure(figsize=(15,10))
        sb.distplot(df["d"].astype(float),bins = 1000)
        cr, val, _ = anderson(df["d"],"norm")
        plt.title("".join([title," Anderson-Darling: ",str(cr)," reject normal @ 5% ",str(cr>val[2])]))#http://docs.scipy.org/doc/scipy-0.14.0/reference/generated/scipy.stats.anderson.html
        #plt.ylim(0,10)
        plt.xlabel("L2 Norm")
        plt.savefig(output)
        print anderson(df["d"],"norm")
    
    plotDist(sys.argv[1], sys.argv[2], sys.argv[3])