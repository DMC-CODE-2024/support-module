package com.gl.ceir.supportmodule.model;

import com.gl.ceir.supportmodule.Constants.ClientTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RedmineResponse {
    private Issue issue;
}