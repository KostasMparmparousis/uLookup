package gr.gunet.uLookup.tools.generator;

import gr.gunet.uLookup.db.personInstances.AcademicPerson;
import org.ldaptive.LdapEntry;
import java.util.Collections;
import java.util.Vector;

public class UserNameGen {
    private final int upperAmountOfNamesSuggested;
    private final String orderBy;
    private final String[] separators;
    private final String[] prioritizeBy;
    private final int lowerLimit;
    private final int upperLimit;
    GeneratingMethods gen;

    public UserNameGen(AcademicPerson academicPerson){
        separators= new String[]{"."};
        gen= new GeneratingMethods(academicPerson, separators, "", "");
        prioritizeBy=new String[]{"fullNames", "prefixedLastName", "partOfNames"};
        lowerLimit=6;
        upperLimit=20;
        upperAmountOfNamesSuggested=6;
        orderBy="alphabetically";
    }

    public UserNameGen(LdapEntry dsPerson){
        separators= new String[]{"."};
        gen= new GeneratingMethods(dsPerson, separators, "", "");
        prioritizeBy=new String[]{"fullNames", "prefixedLastName", "partOfNames"};
        lowerLimit=6;
        upperLimit=20;
        upperAmountOfNamesSuggested=6;
        orderBy="alphabetically";
    }
    
    public UserNameGen(String FN, String LN){
        separators= new String[]{"."};
        gen= new GeneratingMethods(FN, LN, separators, "", "");
        prioritizeBy=new String[]{"fullNames", "prefixedLastName", "partOfNames"};
        lowerLimit=6;
        upperLimit=20;
        upperAmountOfNamesSuggested=6;
        orderBy="alphabetically";
    }

    public Vector<String> proposeNames(){
        Vector<String> proposedNames = new Vector<>();
        Vector<String> fullNames=gen.FullNames();
        Vector<String> partOfNames, percentOfNames, prefixedLastNames;
        if(gen.getFirstName()==null || gen.getLastName()==null) return null;

        int FNchars = 3;
        String FNtakeCharsFrom = "start";
        int LNchars = 4;
        String LNtakeCharsFrom = "start";

        partOfNames=gen.partOfNames(FNchars, FNtakeCharsFrom, LNchars, LNtakeCharsFrom);
        prefixedLastNames=gen.prefixedLastNames(FNchars);
        double FNpercentageOfName = 0.5;
        String FNtakePercentFrom = "";
        double LNpercentageOfName = 0.5;
        String LNtakePercentFrom = "";
        percentOfNames=gen.percentOfNames(FNpercentageOfName, FNtakePercentFrom, LNpercentageOfName, LNtakePercentFrom);

        for (String priority: prioritizeBy){
            switch (priority){
                case "fullNames":
                    addToArray(proposedNames, fullNames);
                    break;
                case "prefixedLastName":
                   addToArray(proposedNames, prefixedLastNames);
                   break;
                case "partOfNames":
                    addToArray(proposedNames, partOfNames);
                    break;
                case "percentOfNames":
                    addToArray(proposedNames, percentOfNames);
                    break;
                default :
                    System.out.println("prioritization technique not found: " + priority);
            }
        }

        proposedNames= charAndSizeLimit(proposedNames);
        int names=proposedNames.size();
        Vector<String> randomNames;
        if (upperAmountOfNamesSuggested!=0){
            randomNames=gen.randomNames(lowerLimit, upperLimit, upperAmountOfNamesSuggested-names);
        }
        else{
            randomNames=gen.randomNames(lowerLimit, upperLimit, 5);
        }
        addToArray(proposedNames, randomNames);

        return proposedNames;
    }

    private void addToArray(Vector<String> proposedNames, Vector<String> Names){
        if (orderBy.equals("alphabetically")){
            Collections.sort(Names);
        }
        else if (orderBy.equals("size")){
            sortBySize(Names);
        }
        Vector<String> newNames= new Vector<>();
        for (String Name: Names) newNames.add(Name.toLowerCase());
        proposedNames.addAll(proposedNames.size(), newNames);
    }

    private void sortBySize(Vector<String> vec){
        for (int i=1 ;i<vec.size(); i++)
        {
            String temp = vec.get(i);

            int j = i - 1;
            while (j >= 0 && temp.length() < vec.get(j).length())
            {
                vec.set(j+1,vec.get(j));
                j--;
            }
            vec.set(j+1,temp);
        }
    }
    public Vector<String> charAndSizeLimit(Vector<String> proposedNames){
        Vector<String> ProposedNames= new Vector<>();
        for (String Name: proposedNames){
            if (lowerLimit!=0 && Name.length()<lowerLimit) {
                continue;
            }
            if (upperLimit!=0 && Name.length()>upperLimit) {
                continue;
            }
            ProposedNames.add(Name);
        }
        if (upperAmountOfNamesSuggested==0) return ProposedNames;

        int names=ProposedNames.size();
        if (names > upperAmountOfNamesSuggested) {
            ProposedNames.subList(upperAmountOfNamesSuggested, names).clear();
        }
        return ProposedNames;
    }

}
