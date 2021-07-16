package com.sarencurrie.capbridge

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler

class Handler : RequestHandler<Any, String?> {
    override fun handleRequest(unused: Any, unused2: Context?): String {
        checkCap()
        return "200 OK"
    }
}