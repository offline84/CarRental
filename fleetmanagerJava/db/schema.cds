namespace sap.javaapp.fleetmanager;

using { managed, cuid, Country, sap.common.CodeList } from '@sap/cds/common';
using { sap.javaapp.fleet.CAR} from '@sap/javaapp-fleet';

entity VEHICLE as projection on CAR; 
extend CAR with {
    MILEAGE             : Integer;
    REQUIRED_LICENSE    : Association to one LICENSE not null;
}

entity PERSON   : managed {
    key NRN             : String(11);
    FIRST_NAME          : String(30) not null;
    LAST_NAME           : String(100) not null;
    EMAIL               : String(30);
    TELEPHONE           : String(100);
    ADRES              : Association to one ADRES not null;
    DRIVERS_LICENSE     : Association to many LICENSE_ASSIGNMENT on DRIVERS_LICENSE.DRIVER=$self;
    TICKET              : Association to many TICKET on TICKET.DRIVER = $self;
}

entity ADRES : cuid {
    STREET      : String(50);
    NUMBER      : Integer;
    POSTAL_CODE : String(6);
    CITY        : String(30);
    COUNTRY     : Country;
}

@Capabilities.Updatable: false
entity LICENSE : CodeList{
    key ID  :Integer;
    LICENSE_ASSIGNMENT :Association to many LICENSE_ASSIGNMENT on LICENSE_ASSIGNMENT.LICENSE=$self;
}

entity LICENSE_ASSIGNMENT: cuid{
    DRIVER      : Association to one PERSON not null;
    LICENSE     : Association to one LICENSE not null;
}

entity TICKET : managed {
    key ID      : Integer;
    DRIVER      : Association to one PERSON not null;
    CAR         : Association to one VEHICLE not null;
    MILEAGE     : Integer;
    START       : DateTime not null;
    END         : DateTime;
}