namespace sap.javaapp.fleet;

using {managed, Currency, sap.common.CodeList} from '@sap/cds/common';

entity CAR: managed {
        key ID      : Integer;
        BRAND       : String(100) not null;
        MODEL       : String(100) not null;
        COLOR       : String(100);
        TYPE_OF_CAR : Association to TYPE_OF_CAR;
        VIN_NUMBER  : String(100) not null;
        FIRST_USE   : Date;
        PRICE       : Decimal(7,2);
        CURRENCY    : Currency;
    }

    entity TYPE_OF_CAR : CodeList {
        key ID      : Integer;
        CARTYPE     : String(100) not null;
        DOOR_COUNT  : Integer;
    }
