package com.hermesworld.ais.galapagos.changes;

import java.util.List;

public interface ChangesService {

    public List<ChangeData> getChangeLog(String environmentId);

}
