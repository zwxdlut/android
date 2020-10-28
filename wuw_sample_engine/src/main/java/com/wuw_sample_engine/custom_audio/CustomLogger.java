////////////////////////////////////////////////////////////////////////////////
///
/// @file            CustomLogger.java
/// @authors
/// @date
///
/// @brief           Implementation for Custom Logger.
////////////////////////////////////////////////////////////////////////////////

package com.wuw_sample_engine.custom_audio;

import nuance.common.ILogModule;
import nuance.common.ILogging;
import nuance.common.LogZone;


public class CustomLogger
{
  public  ILogging logging = ILogging.getInstance();
  public  ILogModule logModule = null;

  //-----------------------------------------------------------------------------------------------------------------------------
  // Description:    This method is used to print logs
  // Parameters:     1)zone   LogZone fo the messgae
  //                 2)msg    Message to be printed.
  //-----------------------------------------------------------------------------------------------------------------------------
  public void logMessage(LogZone zone, String msg)
  {
    if(null != logging && null != logModule)
    {
      logging.logText(logModule,zone,msg,new Exception().getStackTrace()[1].getFileName(), new Exception().getStackTrace()[1].getLineNumber());
    }
  }
}
