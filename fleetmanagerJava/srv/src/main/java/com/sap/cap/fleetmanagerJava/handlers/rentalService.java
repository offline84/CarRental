package com.sap.cap.fleetmanagerJava.handlers;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sap.cds.Row;
import com.sap.cds.ql.Insert;
import com.sap.cds.ql.Select;
import com.sap.cds.ql.cqn.CqnInsert;
import com.sap.cds.services.ErrorStatuses;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.cds.CdsService;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.Before;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import cds.gen.rentalservice.Adresses;
import cds.gen.rentalservice.Adresses_;
import cds.gen.rentalservice.AssignedLicenses;
import cds.gen.rentalservice.AssignedLicenses_;
import cds.gen.rentalservice.GetLicensesForDriverContext;
import cds.gen.rentalservice.Licenses;
import cds.gen.rentalservice.Licenses_;
import cds.gen.rentalservice.Persons;
import cds.gen.rentalservice.Persons_;
import cds.gen.rentalservice.PostPersonContext;
import cds.gen.rentalservice.RentalService_;
import cds.gen.rentalservice.Tickets;
import cds.gen.rentalservice.Tickets_;
import cds.gen.sap.javaapp.fleetmanager.Vehicle;
import cds.gen.sap.javaapp.fleetmanager.Vehicle_;

@Component
@ServiceName(RentalService_.CDS_NAME)
public class rentalService implements EventHandler {
    @Autowired
    PersistenceService db;

    // Transactional creates a rollback system for inserts in this method, the
    // propagation assures that a new instance is created
    // when this method is invoked, otherwise the annotation creates only one
    // instance.
    @Transactional(rollbackFor = ServiceException.class, propagation = Propagation.REQUIRES_NEW)
    @On(event = PostPersonContext.CDS_NAME)
    public void postPerson(PostPersonContext ctx) {
        Persons person = ctx.getPerson();
        Adresses adres = person.getAdres();
        ValidatePerson(person);

        //Check if adres exists, if not add to db.
        List<Adresses> retrievedAdres = GetAdresIfExists(adres);

        if (retrievedAdres.isEmpty()) {
            try 
            {
                CqnInsert adresToInsert = Insert.into(Adresses_.CDS_NAME).entry(adres);
                db.run(adresToInsert).single(Adresses.class);
            } 
            catch (Exception e) 
            {
                throw new ServiceException(ErrorStatuses.BAD_REQUEST, e.toString());
            }
        }

        //Add person to db
        Persons savedPerson;
        try 
        {
            CqnInsert personToInsert = Insert.into(Persons_.CDS_NAME).entry(person);
            savedPerson = db.run(personToInsert).single(Persons.class);
        } 
        catch (Exception e) 
        {
            throw new ServiceException(ErrorStatuses.BAD_REQUEST, e.toString());
        }

        //retrieve licenses from context and Adding them one by one to a mapped list, then do a bulk insert.
        Collection<Licenses> licenses = ctx.getLicenses();

        List<Map<String, Object>>mLicensesToAssign = new ArrayList<>();
        for (Licenses license: licenses) {
            Map<String, Object> mLic = new HashMap<>();
                mLic.put("DRIVER", person);
                mLic.put("LICENSE", license);
                mLicensesToAssign.add(mLic);
        }
            try
            { 
                CqnInsert licenseToAssign = Insert.into(AssignedLicenses_.CDS_NAME).entries(mLicensesToAssign);
                db.run(licenseToAssign);
            }
            catch (Exception e) 
            {
                throw new ServiceException(ErrorStatuses.BAD_REQUEST, e.toString());
            }


        ctx.setResult("Person is added: " + savedPerson);
    }

    @On(event = GetLicensesForDriverContext.CDS_NAME)
    public void GetLicencesForDriver(GetLicensesForDriverContext ctx){

        //extract the parameter from the context
        String rrn = ctx.getNrn();

        //Get all licenses for the person
        Select<AssignedLicenses_> getAssignments = Select.from(AssignedLicenses_.class)
                .where(l -> l.DRIVER_NRN().eq(rrn));
        List<AssignedLicenses> assignments = db.run(getAssignments).listOf(AssignedLicenses.class);

        //Make a list of all ID,s of licenses the driver has
        List<Integer>mLicenses = new ArrayList<>();
        for (AssignedLicenses assignedLicense : assignments) {
            Integer filter = assignedLicense.getLicenseId();
            mLicenses.add(filter);
        }

        //retrieve the licenses from the database throug a where in clause
        Select getLicenses = Select.from(Licenses_.class).where(a -> a.ID().in(mLicenses));
        Collection<Licenses> licenses = db.run(getLicenses).listOf(Licenses.class);
        ctx.setResult(licenses);
    }

    @Before(event = CdsService.EVENT_CREATE, entity = Adresses_.CDS_NAME)
    public void CreateAdresOnlyOnce(Adresses adres) {
        List<Adresses> retrievedAdres = GetAdresIfExists(adres);

        if (!retrievedAdres.isEmpty()) {
            throw new ServiceException(ErrorStatuses.BAD_REQUEST, "Adres already exists");
        }
    }

    @Before(event = CdsService.EVENT_CREATE, entity = Tickets_.CDS_NAME)
    public void ValidateLicenseForCar(Tickets ticket) {

        // get list of license_id's of driver
        String rrn = ticket.getDriverNrn();

        //  retrieve required license to operate vehicle from car
        Select getCar = Select.from(Vehicle_.class).columns(c -> c.REQUIRED_LICENSE_ID()).where(c -> c.ID().eq(ticket.getCarId()));
        Optional<Vehicle> car = db.run(getCar).first(Vehicle.class);

        Vehicle c = car.get();
        Map<String, Object> filter = new HashMap<>();
        filter.put("LICENSE_ID", c.getRequiredLicenseId());
        filter.put("DRIVER_NRN", rrn);
        
        Select<AssignedLicenses_> getLics = Select.from(AssignedLicenses_.class).matching(filter);
        Optional<Row> driverLic = db.run(getLics).first();

        if (!driverLic.isPresent()) {
             throw new ServiceException(ErrorStatuses.BAD_REQUEST,
                     "Driver has not the correct license to operate this car");
        }
    }

    public void ValidatePerson(Persons person) {
        String rrn = person.getNrn();
        Pattern pattern = Pattern.compile("[0-9]{11}");
        Matcher matchRrn = pattern.matcher(rrn);
        Boolean correctRrn = matchRrn.find();

        if (!correctRrn) {
            throw new ServiceException(ErrorStatuses.BAD_REQUEST, "incorrect NRN");
        }
        ValidateLegalAge(rrn);
    }

    private void ValidateLegalAge(String rrn) {

        // break down current yiear in first- and last two digits
        LocalDate now = LocalDate.now();
        int currentyear = now.getYear();
        String year = Integer.toString(currentyear);
        String ar = year.substring(2, 4);
        String ye = year.substring(0, 2);

        // break down birthdate in couples of two digits(year, month, day)
        String sBirthAr = rrn.substring(0, 2);
        String sBirthMonth = rrn.substring(2, 4);
        String sBirthDay = rrn.substring(4, 6);

        // Check if >= millenial and if so set correct era
        String sBirthYear = "";
        int era = Integer.parseInt(ye);
        if (Integer.parseInt(ar) < Integer.parseInt(sBirthAr)) {
            era -= 1;
        }

        sBirthYear = Integer.toString(era) + sBirthAr;

        // check if legal driving age, if not throw ServiceException
        int birthYear = Integer.parseInt(sBirthYear);
        LocalDate birthDate = LocalDate.of(birthYear, Integer.parseInt(sBirthMonth), Integer.parseInt(sBirthDay));
        LocalDate legalAge = now.minusYears(18);
        if (legalAge.isBefore(birthDate)) {
            throw new ServiceException(ErrorStatuses.BAD_REQUEST, "Person is not 18 yo yet!");
        }
    }

    private List<Adresses> GetAdresIfExists(Adresses adres) {
        Map<String, Object> filter = new HashMap<>();
        filter.put("POSTAL_CODE", adres.getPostalCode());
        filter.put("NUMBER", adres.getNumber());
        filter.put("STREET", adres.getStreet());

        Select<Adresses_> adresToRetrieve = Select.from(Adresses_.class).matching(filter);
        List<Adresses> retrievedAdres = db.run(adresToRetrieve).listOf(Adresses.class);
        return retrievedAdres;
    }

}