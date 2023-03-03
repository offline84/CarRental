using {sap.javaapp.fleetmanager as db} from '../db/schema.cds';
using { AdminService } from '@sap/javaapp-fleet';

service RentalService {

    type Message {
        Code: Integer;
        Message: String;
    };
    @readonly
    entity Persons as projection on db.PERSON excluding {createdAt, createdBy, modifiedAt, modifiedBy};
    // actions 
    // {
    //     function GetLicensesForDriver() returns String;
    // };
    function GetLicensesForDriver(nrn: String) returns array of Licenses;
    action postPerson(person: Persons, licenses: array of Licenses) returns String;
    entity Adresses as projection on db.ADRES;
    @Capabilities: { Insertable : false, Updatable: true } 
    entity Vehicles as projection on db.VEHICLE excluding {createdAt, createdBy, modifiedAt, modifiedBy, VIN_NUMBER, PRICE, CURRENCY};
    @readonly
    entity Licenses as projection on db.LICENSE;
    @Capabilities : { Deletable: false }
    entity Tickets as projection on db.TICKET excluding {createdAt, createdBy, modifiedAt, modifiedBy};
    entity AssignedLicenses as projection on db.LICENSE_ASSIGNMENT;

}



extend service AdminService with {
    entity Person as projection on db.PERSON;
    entity Adress as projection on db.ADRES;
    entity Ticket as projection on db.TICKET;
    entity License as projection on db.LICENSE;
}