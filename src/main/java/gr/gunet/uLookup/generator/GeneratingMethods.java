package gr.gunet.uLookup.generator;
import gr.gunet.uLookup.AcademicPerson;
import java.util.Random;
import java.util.Vector;
import org.ldaptive.LdapEntry;

public class GeneratingMethods {
    AcademicPerson academicPerson;
    LdapEntry dsPerson;
    String[] Separators;
    String prefix;
    String FNplacement;
    String LNplacement;
    String inscriptionYear;
    String firstName;
    String lastName;

    public GeneratingMethods(AcademicPerson academicPerson, String[] Separators, String FNplacement, String LNplacement){
        this.academicPerson=academicPerson;
        this.dsPerson=null;
        this.Separators=Separators;
        this.FNplacement=FNplacement;
        this.LNplacement=LNplacement;
        this.firstName=academicPerson.getFirstNameEn();
        this.lastName=academicPerson.getLastNameEn();
    }

    public GeneratingMethods(LdapEntry dsPerson, String[] Separators, String FNplacement, String LNplacement){
        this.academicPerson=null;
        this.dsPerson=dsPerson;
        this.Separators=Separators;
        this.FNplacement=FNplacement;
        this.LNplacement=LNplacement;
        if (dsPerson.getAttribute("displayName")!=null) {
            String displayName = dsPerson.getAttribute("displayName").getStringValue();
            String names[] = displayName.split(" ");
            this.firstName = names[0];
            this.lastName = names[1];
        }
    }
    
    public GeneratingMethods(String FN, String LN, String[] Separators, String FNplacement, String LNplacement){
        this.academicPerson=null;
        this.dsPerson=null;
        this.Separators=Separators;
        this.FNplacement=FNplacement;
        this.LNplacement=LNplacement;
        this.firstName=FN;
        this.lastName=LN;
    }

    public  Vector<String> FullNames(){
        Vector<String> vec = new Vector<String>();
        int count=0;
        for (String Separator: Separators){
            Vector<String> Names= orderOfNames(firstName, lastName, Separator);
            vec.addAll(count,Names);
            count+=Names.size();
        }
         return vec;
    }

    public Vector<String> partOfNames(int FNchars, String FNtakeFrom, int LNchars, String LNtakeFrom){
        Vector<String> vec = new Vector<String>();
        if (FNchars==0 && LNchars==0) return vec;
        int FNlength=firstName.length();
        int LNlength=lastName.length();
        
        if (FNchars > FNlength && LNchars>LNlength) return vec;
        else{
            if (FNchars>FNlength) FNchars=FNlength;
            if (LNchars>LNlength) LNchars= LNlength;
        }
        int count=0;
        for (String Separator: Separators){
            String FN,LN;
            
            FN=getSubstring(firstName, FNchars, FNtakeFrom, FNlength);
            LN=getSubstring(lastName, LNchars, LNtakeFrom, LNlength);

            Vector<String> Names= orderOfNames(FN, LN, Separator);
            vec.addAll(count,Names);
            count+=Names.size();
        }
         return vec;
    }

    public Vector<String> percentOfNames(double FNpercent, String FNtakeFrom, double LNpercent, String LNtakeFrom){
        Vector<String> vec = new Vector<String>();
        if (FNpercent==0.0 && LNpercent==0.0) return vec;
        int count=0;
        int FNlength= (firstName.length());
        int LNlength= (lastName.length());
        double fnchars= FNlength*FNpercent;
        double lnchars= LNlength*LNpercent;
        int FNchars= (int) fnchars;
        int LNchars= (int) lnchars;

        for (String Separator: Separators){
            String FN,LN;
            
            FN=getSubstring(firstName, FNchars, FNtakeFrom, FNlength);
            LN=getSubstring(lastName, LNchars, LNtakeFrom, LNlength);

            Vector<String> Names= orderOfNames(FN, LN, Separator);
            vec.addAll(count,Names);
            count+=Names.size();
        }
         return vec;
    }

    public Vector<String> prefixedLastNames(int FNchars){
      Vector<String> vec = new Vector<String>();
      int FNlength=firstName.length();
      if (FNchars==0) return vec;
      else if (FNchars > FNlength) FNchars=FNlength;

      int count=0;
      for (String Separator: Separators){
        for (int i=FNchars; i>0; i--){
          String FN,LN;
          FN=getSubstring(firstName, i, "start", FNlength);
          LN=lastName;
          Vector<String> Names= orderOfNames(FN, LN, Separator);
          vec.addAll(count,Names);
          count+=Names.size();
        }
      }
      return vec;
    }

    public Vector<String> orderOfNames(String FirstName, String LastName, String Separator){
        Vector<String> vec = new Vector<String>();
        if (FNplacement==null) FNplacement="";
        if (LNplacement==null) LNplacement="";

        if (FNplacement.equals("start"))
        {
            if (LNplacement.equals("") || LNplacement==null || LNplacement.equals("end")){
                vec.add(FirstName+Separator+LastName);
                return vec;
            }
        }
        if (LNplacement.equals("start"))
        {
            if (FNplacement.equals("") || FNplacement==null || FNplacement.equals("end")){
                vec.add(LastName+Separator+FirstName);
                return vec;
            }
        }

        vec.add(LastName+Separator+FirstName);
        vec.add(FirstName+Separator+LastName);
        return vec;
    }

    public String getSubstring(String Name, int chars, String TakeFrom, int length){
        Random rand= new Random();
        String N="";
        if (chars==0) return Name;
        if (TakeFrom==null) TakeFrom="";
        switch(TakeFrom){
            case "start":
                N=Name.substring(0,chars);
                break;
            case "end":
                N=Name.substring(length-chars , length);
                break;
            default:
                int randomInd=0;
                do{
                    randomInd= rand.nextInt(length - chars + 1);
                } while((randomInd+chars)>length);
                N=Name.substring(randomInd, randomInd+chars);
        }
        return N;
    }

    public Vector<String> randomNames(int minLimit, int maxLimit, int namesNeeded, Vector<String> proposedNames){
        Vector<String> vec = new Vector<String>();
        Random rand = new Random();
        int size1,size2;
        if (minLimit==0) minLimit=5;
        if (maxLimit==0) maxLimit=100;
        for (int i=0; i<namesNeeded; i++){
             boolean check = false;
            int j=0;
            while (check != true) {
                String name;
                size1 = rand.nextInt(firstName.length());
                size2 = rand.nextInt(lastName.length()) ;

                if ((size1 + size2) >= minLimit && (size1 + size2) <= maxLimit) {
                    name = firstName.substring(0, size1) + lastName.substring(0, size2);
                    boolean Test=inVector(vec, name);
                        if (Test==false) {
                            vec.add(name);
                            check = true;
                        }
                }
                if (j==20*namesNeeded) check=true;
                j++;
            }
        }
        return vec;
    }

    public boolean inVector(Vector<String> proposedNames, String userName){
        for (String name: proposedNames){
            if (name.equals(userName)) return true;
        }
        return false;
    }

    int hasDelimiter (String ID){
        int count=0;
        for (char ch: ID.toCharArray()) {
            if (Character.isDigit(ch)==false) return count;
            count++;
        }
        return -1;
    }

    public String capitalize(String str){
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    public String getFirstName(){
        return this.firstName;
    }

    public String getLastName(){
        return this.lastName;
    }
}
