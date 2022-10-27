package gr.gunet.uLookup.endpoints.validator;

import gr.gunet.uLookup.db.personInstances.AcademicPerson;
import gr.gunet.uLookup.tools.Conflict;
import gr.gunet.uLookup.db.personInstances.RequestPerson;
import gr.gunet.uLookup.tools.ResponseMessages;
import gr.gunet.uLookup.db.HRMSDBView;
import gr.gunet.uLookup.db.SISDBView;
import gr.gunet.uLookup.db.ldap.LdapManager;
import gr.gunet.uLookup.db.personInstances.SchGrAcPerson;
import gr.gunet.uLookup.db.ldap.LdapConnectionPool;
import gr.gunet.uLookup.db.DBConnectionPool;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Vector;

import org.ldaptive.LdapEntry;
import org.ldaptive.LdapException;

public class Validator {
    private final DBConnectionPool Views;
    private final LdapConnectionPool ldapDS;
    String disabledGracePeriod;
    RequestPerson reqPerson;
    ResponseMessages responses;

    public Validator(String institution, String gracePeriod, ResponseMessages responses) {
        this.Views= new DBConnectionPool(institution);
        this.ldapDS= new LdapConnectionPool(institution);
        this.disabledGracePeriod=gracePeriod;
        this.responses=responses;
    }

    public String validateLoginName(RequestPerson reqPerson, boolean fromWeb) throws LdapException,Exception{
        this.reqPerson=reqPerson;
        Collection<String> UIDPersons;
        Collection<LdapEntry> DSAccounts;
        try {
            UIDPersons=getUIDPersons();
            if (!UIDPersons.isEmpty()){
                String uid= UIDPersons.iterator().next();
                System.out.println("-Response code: 300");
                System.out.println("-message: \"" + uid + "already exists while not following the typical DS Account generation procedure\"");
                System.out.println("-----------------------------------------------------------");
                System.out.println();
                return responses.getResponse("300", "", "");
            }
        } catch (Exception e) {
            return errorMessage(e,"DS");
        }
        try {
            DSAccounts=getDSAccounts();
            if (!DSAccounts.isEmpty()){
                if (disabledGracePeriod==null || getRetentionAccounts(DSAccounts).isEmpty()){ //outside retention period
                    return responses.getResponse("310", "", "");
                }
                else{  //inside retention period
                    return responses.getResponse("310", "", ""); // exact response to be determined.
                }
            }
        } catch (Exception e) {
            return errorMessage(e,"DS");
        }
        try {
            Collection<Conflict> conflicts= checkForUniquenessConflicts();
            Collection<String> previousLoginNames= findPreviousLoginNames();
            Collection<String> nameSources= getLoginNameSources();
            HashMap<String, String> results= new ValidatorResults(reqPerson, conflicts, previousLoginNames, nameSources, responses).getResults(fromWeb);

            return responses.getResponse(results.get("code"), results.get("content"), results.get("title"));
        }catch(Exception e){
            String errorSource= e.getMessage();
            return errorMessage(e,errorSource);
        }
    }

    private Collection<String> getUIDPersons() throws LdapException,Exception{
        Collection<String> UIDPersons = new HashSet<>();
        LdapManager ds;
        try {
            ds= ldapDS.getConn();
            Collection<LdapEntry> existingDSOwners= ds.search(ds.createSearchFilter("(&(!(objectClass=schGrAcLinkageIdentifiers))(!(objectClass=schacLinkageIdentifiers)))","uid="+reqPerson.getLoginName()));
            if (!existingDSOwners.isEmpty()){
                for (LdapEntry uidPerson: existingDSOwners){
                    if (uidPerson.getAttribute("uid")!=null && (uidPerson.getAttribute("uid").getStringValue()).equals(reqPerson.getLoginName())){
                        UIDPersons.add(reqPerson.getLoginName());
                    }
                }
            }
        }
        catch(LdapException e){
            e.printStackTrace(System.err);
            throw new Exception("DS");
        }
        return UIDPersons;
    }

    private Collection<LdapEntry> getDSAccounts() throws LdapException,Exception{
        Collection<LdapEntry> DSAccounts = new HashSet<>();
        LdapManager ds;
        try {
            ds= ldapDS.getConn();
            Collection<LdapEntry> existingDSOwners= ds.search(ds.createSearchFilter("(objectClass=account)","uid="+reqPerson.getLoginName()));
            if (!existingDSOwners.isEmpty()){
                for (LdapEntry dsAccount: existingDSOwners){
                    if (dsAccount.getAttribute("uid")!=null && (dsAccount.getAttribute("uid").getStringValue()).equals(reqPerson.getLoginName())){
                        DSAccounts.add(dsAccount);
                    }
                }
            }
        }
        catch(LdapException e){
            e.printStackTrace(System.err);
            throw new Exception("DS");
        }
        return DSAccounts;
    }

    private Collection <AcademicPerson> getRetentionAccounts(Collection<LdapEntry> DSAccounts) throws LdapException,Exception{
        Collection<AcademicPerson> linkageAccounts = new Vector<>();
        HashMap<String, String> attributes;
        attributes = new HashMap<>();
        attributes.put("loginName", reqPerson.getLoginName());
        attributes.put("disabledGracePeriod", disabledGracePeriod);
        for (LdapEntry dsAccount: DSAccounts){
            Collection<String> linkages= dsAccount.getAttribute("schGrAcPersonalLinkageID").getStringValues();
            if (!linkages.isEmpty()){
                for (String linkage: linkages){
                    if (linkage.toLowerCase().contains("registrationid")){
                        linkageAccounts.addAll(getFromLinkage(linkage));
                        if (!linkageAccounts.isEmpty()) return linkageAccounts;
                    }
                }
                return linkageAccounts;
            }
        }

        Collection<AcademicPerson> existingOwners;
        SISDBView sis;
        HRMSDBView hrms, hrms2;
        try{
            sis=Views.getSISConn();
            existingOwners= sis.fetchAll(attributes);
            if (!existingOwners.isEmpty()) return existingOwners;
        }
        catch(Exception e){
            e.printStackTrace(System.err);
            throw new Exception("SIS");
        }

        try{
            hrms=Views.getHRMSConn();
            if (hrms!=null){
                existingOwners.addAll(hrms.fetchAll(attributes));
                if (!existingOwners.isEmpty()) return existingOwners;
            }
        }
        catch(Exception e){
            e.printStackTrace(System.err);
            throw new Exception("HRMS");
        }

        try{
            hrms2=Views.getHRMS2Conn();
            if (hrms2!=null){
                existingOwners.addAll(hrms2.fetchAll(attributes));
                if (!existingOwners.isEmpty()) return existingOwners;
            }
        }
        catch(Exception e){
            e.printStackTrace(System.err);
            throw new Exception("HRMS2");
        }
        return existingOwners;
    }

    private Collection<AcademicPerson> getFromLinkage(String linkage) throws Exception{
        SISDBView sis;
        HRMSDBView hrms, hrms2;
        Collection<AcademicPerson> existingOwners = null;
        HashMap<String, String> attributes;
        String registrationID= linkage.substring(linkage.lastIndexOf(":") + 1);
        attributes = new HashMap<>();
        attributes.put("loginName", reqPerson.getLoginName());
        attributes.put("disabledGracePeriod", disabledGracePeriod);
        attributes.put("registrationID", registrationID);

        if (linkage.contains("sis")){
            try{
                sis=Views.getSISConn();
                existingOwners= sis.fetchAll(attributes);
                if (!existingOwners.isEmpty()) return existingOwners;
            }
            catch(Exception e){
                e.printStackTrace(System.err);
                throw new Exception("SIS");
            }
        }
        else if (linkage.contains("hrms:1")){
            try{
                hrms=Views.getHRMSConn();
                if (hrms!=null){
                    existingOwners.addAll(hrms.fetchAll(attributes));
                    if (!existingOwners.isEmpty()) return existingOwners;
                }
            }
            catch(Exception e){
                e.printStackTrace(System.err);
                throw new Exception("HRMS");
            }
        }
        else{
            try{
                hrms2=Views.getHRMS2Conn();
                if (hrms2!=null){
                    existingOwners.addAll(hrms2.fetchAll(attributes));
                    if (!existingOwners.isEmpty()) return existingOwners;
                }
            }
            catch(Exception e){
                e.printStackTrace(System.err);
                throw new Exception("HRMS2");
            }
        }
        return existingOwners;
    }

    private  Collection<Conflict> checkForUniquenessConflicts() throws LdapException, Exception{
        Collection<Conflict> conflicts = new HashSet<>();
        Collection<LdapEntry> existingDSOwners;
        Collection<AcademicPerson> existingOwners;
        HashMap<String, String> attributes = new HashMap<>();
        attributes.put("loginName", reqPerson.getLoginName());
        if (disabledGracePeriod!=null) attributes.put("disabledGracePeriod", disabledGracePeriod);

        SISDBView sis;
        HRMSDBView hrms, hrms2;
        LdapManager ldap;
        try{
            sis=Views.getSISConn();
            existingOwners= sis.fetchAll(attributes);
            for(AcademicPerson existingOwner : existingOwners){
                conflicts.addAll(samePersonChecks(reqPerson,existingOwner,"SIS DB View"));
            }
        }
        catch(Exception e){
            e.printStackTrace(System.err);
            throw new Exception("SIS");
        }

        try{
            hrms=Views.getHRMSConn();
            if (hrms!=null){
                existingOwners= hrms.fetchAll(attributes);
                for(AcademicPerson existingOwner : existingOwners){
                    conflicts.addAll(samePersonChecks(reqPerson,existingOwner,"HRMS DB View"));
                }
            }
        }
        catch(Exception e){
            e.printStackTrace(System.err);
            throw new Exception("HRMS");
        }

        try{
            hrms2=Views.getHRMS2Conn();
            if (hrms2!=null){
                existingOwners= hrms2.fetchAll(attributes);
                for(AcademicPerson existingOwner : existingOwners){
                    conflicts.addAll(samePersonChecks(reqPerson,existingOwner,"Associates DB View"));
                }
            }
        }
        catch(Exception e){
            e.printStackTrace(System.err);
            throw new Exception("HRMS2");
        }

        try{
            ldap=ldapDS.getConn();
            existingDSOwners = ldap.search(ldap.createSearchFilter("uid="+reqPerson.getLoginName()));
            for(LdapEntry existingDSOwner : existingDSOwners){
                conflicts.addAll(samePersonChecks(reqPerson,new SchGrAcPerson(existingDSOwner, reqPerson.getLoginName()),"DS"));
            }
        }
        catch(LdapException e){
            e.printStackTrace(System.err);
            throw new Exception("DS");
        }
        return conflicts;
    }

    public Collection<String> findPreviousLoginNames(){
        HashMap<String, String> attributes = new HashMap<>();
        attributes.put("loginName", reqPerson.getLoginName());
        attributes.put("SSN", reqPerson.getSSN());
        attributes.put("ssnCountry", reqPerson.getSSNCountry());
        if (reqPerson.getTIN()!=null && reqPerson.getTINCountry()!=null){
            attributes.put("tin", reqPerson.getTIN());
            attributes.put("tinCountry", reqPerson.getTINCountry());
        }
        if (reqPerson.getBirthDate()!=null){
            attributes.put("birthDate", reqPerson.getBirthDate());
        }
        if (disabledGracePeriod!=null) attributes.put("disabledGracePeriod", disabledGracePeriod);
        SISDBView sis;
        HRMSDBView hrms,hrms2;
        LdapManager ldap;
        LinkedList<String> existingUserNames= new LinkedList<>();
        Collection<LdapEntry> existingDSOwners = new LinkedList<>();
        Collection<AcademicPerson> existingOwners;

        try {
            sis = Views.getSISConn();
            hrms = Views.getHRMSConn();
            hrms2 = Views.getHRMS2Conn();
            ldap = ldapDS.getConn();

            existingOwners = sis.fetchAll(attributes);
            if (hrms != null) existingOwners.addAll(hrms.fetchAll(attributes));
            if (hrms2 != null) existingOwners.addAll(hrms2.fetchAll(attributes));

            if (!existingOwners.isEmpty()) {
                for (AcademicPerson person : existingOwners) {
                    if (!existingUserNames.contains(person.getLoginName())) {
                        existingUserNames.add(person.getLoginName());
                    }
                }
            }

            existingDSOwners.addAll(ldap.search(ldap.createSearchFilter("schacPersonalUniqueID=*SSN:" + reqPerson.getSSN())));
            for(LdapEntry existingDSOwner : existingDSOwners){
                SchGrAcPerson DSPerson=new SchGrAcPerson(existingDSOwner);
                for (String uid: DSPerson.getUids()){
                    if (!existingUserNames.contains(uid)) {
                        existingUserNames.add(uid);
                    }
                }
            }
        }catch (Exception e){
            e.printStackTrace(System.err);
            closeViews();
        }
        return existingUserNames;
    }

    private Collection<String> getLoginNameSources() throws LdapException,Exception{
        Collection<String> loginNameSources= new HashSet<>();
        LdapManager ldap;
        Collection<LdapEntry> existingDSOwners;

        try{
            ldap=ldapDS.getConn();
            existingDSOwners = ldap.search(ldap.createSearchFilter("uid="+reqPerson.getLoginName()));
            if (!existingDSOwners.isEmpty()){
                loginNameSources.add("DS");
                return loginNameSources;
            }
        }
        catch(LdapException e){
            e.printStackTrace(System.err);
            throw new Exception("DS");
        }
        return loginNameSources;
    }

    private Collection<Conflict> samePersonChecks(AcademicPerson loginNameOwner, AcademicPerson existingOwner, String existingOwnerSource){
        Collection<Conflict> conflicts = new HashSet<>();

        String conflType = "existing-login";
        String conflDesc = "A record exists in '"+existingOwnerSource+"' with the same loginName, but a different";
        String existingOwnerKeys;
        if(existingOwnerSource.equals("DS")){
            existingOwnerKeys = "academicID="+existingOwner.getAcademicID();
        }else{
            existingOwnerKeys = "registrationID="+existingOwner.getRegistrationID()+",systemID="+existingOwner.getSystemID();
        }
        String conflSource = existingOwnerSource.split(" ")[0].toLowerCase();

        if(!loginNameOwner.getTin().isEmpty() && !existingOwner.getTin().isEmpty()){
            Collection<String> requestTins= loginNameOwner.getTin();
            Collection<String> existingTins= existingOwner.getTin();
            for (String requestTin: requestTins){
                if (!existingTins.contains(requestTin)){
                    for(String existingTin: existingTins){
                        conflicts.add(new Conflict(conflType, conflDesc+" TIN", "TIN", existingOwnerKeys,conflSource,requestTin,existingTin));
                    }
                }
            }
        }

        if(!loginNameOwner.getTinCountry().isEmpty() && !existingOwner.getTinCountry().isEmpty()){
            Collection<String> requestTinCountries= loginNameOwner.getTinCountry();
            Collection<String> existingTinCountries= existingOwner.getTinCountry();
            for (String requestTinCountry: requestTinCountries){
                if (!existingTinCountries.contains(requestTinCountry)){
                    for(String existingTinCountry: existingTinCountries){
                        conflicts.add(new Conflict(conflType, conflDesc+" TIN Country", "TIN Country", existingOwnerKeys,conflSource,requestTinCountry,existingTinCountry));
                    }
                }
            }
        }

        if(!loginNameOwner.getSsn().isEmpty() && !existingOwner.getSsn().isEmpty()){
            Collection<String> requestSsns= loginNameOwner.getSsn();
            Collection<String> existingSsns= existingOwner.getSsn();
            for (String requestSsn: requestSsns){
                if (!existingSsns.contains(requestSsn)){
                    for(String existingSsn: existingSsns){
                        conflicts.add(new Conflict(conflType, conflDesc+" SSN", "SSN", existingOwnerKeys,conflSource,requestSsn,existingSsn));
                    }
                }
            }
        }

        if(!loginNameOwner.getSsnCountry().isEmpty() && !existingOwner.getSsnCountry().isEmpty()){
            Collection<String> requestSsnCountries= loginNameOwner.getSsnCountry();
            Collection<String> existingSsnCountries= existingOwner.getSsnCountry();
            for (String requestSsnCountry: requestSsnCountries){
                if (!existingSsnCountries.contains(requestSsnCountry)){
                    for(String existingSsnCountry: existingSsnCountries){
                        conflicts.add(new Conflict(conflType, conflDesc+" SSN Country", "SSN Country", existingOwnerKeys,conflSource,requestSsnCountry,existingSsnCountry));
                    }
                }
            }
        }

        String loginNameOwnerBirthDate=loginNameOwner.getBirthDate();
        String existingOwnerBirthDate= existingOwner.getBirthDate();
        if (loginNameOwnerBirthDate!=null && existingOwnerBirthDate!=null){
            loginNameOwnerBirthDate= removeSpecialChars(loginNameOwnerBirthDate);
            existingOwnerBirthDate= removeSpecialChars(existingOwnerBirthDate);
            if (!loginNameOwnerBirthDate.equals(existingOwnerBirthDate)){
                conflicts.add(new Conflict(conflType, conflDesc+" date of birth", "birthDate", existingOwnerKeys,conflSource, loginNameOwnerBirthDate, existingOwnerBirthDate));
            }
        }

        String loginNameOwnerBirthYear=loginNameOwner.getBirthYear();
        String existingOwnerBirthYear=null;
        if (existingOwnerBirthDate!=null) existingOwnerBirthYear= existingOwner.getBirthYear();
        if (loginNameOwnerBirthYear!=null && existingOwnerBirthYear!=null){
            loginNameOwnerBirthYear= removeSpecialChars(loginNameOwnerBirthYear);
            existingOwnerBirthYear= removeSpecialChars(existingOwnerBirthYear);
            if (!loginNameOwnerBirthYear.equals(existingOwnerBirthYear)){
                conflicts.add(new Conflict(conflType, conflDesc+" year of birth", "birthYear", existingOwnerKeys,conflSource, loginNameOwnerBirthYear, existingOwnerBirthYear));
            }
        }

        return conflicts;
    }
    private String removeSpecialChars (String str){
        return str.replaceAll("[^0-9]", "");
    }

    public String errorMessage(Exception e, String source){
        e.printStackTrace(System.err);
        closeViews();
        if (source!=null){
            System.out.println("-Response code: 500");
            System.out.println("-message: " + "\"Could not connect to the " + source + ".\"");
            System.out.println("-----------------------------------------------------------");
            System.out.println();
            return responses.getResponse("500", source, "");
        }
        else{
            System.out.println("-Response code: 501");
            System.out.println("-message: An error has occurred");
            System.out.println("-----------------------------------------------------------");
            System.out.println();
            return responses.getResponse("501", "An error has occurred.", "");
        }
    }

    public void closeViews(){
        DBConnectionPool.clean();
        LdapConnectionPool.clean();
    }
}
