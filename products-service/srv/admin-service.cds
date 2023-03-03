using { sap.javaapp.fleet as db} from '../db/schema';

service AdminService {
    entity Car as projection on db.CAR;
    entity CarType as projection on db.TYPE_OF_CAR;
}