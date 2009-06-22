
// talk to SuperCollider objects from the network via OpenSoundControl
// basic syntax: /oo objectName selector args ...
// Julian Rohrhuber and Fredrik Olofsson, 2009


OpenObject {

	classvar <>objects;
	classvar <responders;
	classvar <>lookup = false;
	
	*initClass {
		objects = ();
	}
	
	*put { |name, object|
		objects.put(name, object)
	}
	
	*keyFor { |object|
		^objects.findKeyForValue(object)
	}
	
	*remove { |object|
		var key = this.keyFor(object);
		key !? { objects.removeAt(key) }
	}
	
	*removeAt { |name|
		^objects.removeAt(name)
	}


	*start { |addr|
	
		if(NetAddr.langPort != 57120) { 
			Error("SC started under a langPort other than 57120. Try quit and restart.").throw
		};
		if(this.isListening) { "OpenObject: already listening".warn; ^this };
		
		this.addResponder(addr, '/oo', { |msg| this.oscPerform(msg) });
		this.addResponder(addr, '/oo_k', { |msg| this.oscPerformKeyValuePairs(msg) });
		this.addResponderWithReply(addr, '/oor', { |msg| this.oscPerform(msg) });
		this.addResponderWithReply(addr, '/oor_k', { |msg| this.oscPerformKeyValuePairs(msg) });
	}
	
	*end {
		responders.do(_.remove);
		responders = nil;
	}
	
	*clear {
		objects = ();
	}
	
	*isListening {
		^responders.notNil
	}
	
	*openProxies {
		[Pdef, Pdefn, Tdef, Fdef, Ndef].do { |class| class.publish(class.name) };
		lookup = true;
	}
	
	// a dangerous tool for both good and evil ...
	
	*openInterpreter { |addr|
			("Networking opens interpreter - use 'openInterpreter' at your own risk"
			"\nuse 'closeInterpreter' to close interpreter. *").postln;
			this.addResponder(addr, '/oo_p', { |msg| this.setProxySource(msg) });
			this.addResponder(addr, '/oo_i', { |msg| this.interpretOSC(msg) });
			this.addResponderWithReply(addr, '/oor_i', { |msg| this.interpretOSC(msg) });
	}
	
	// safe again.
	
	*closeInterpreter {
		this.removeResponder('/oo_i');
		this.removeResponder('/oo_p');
		this.removeResponder('/oor_i');
	}

	
	////////////// private implementation /////////////////
	
	*addResponder { |addr, cmd, func|
		responders = responders.add(
			OSCresponderNode(addr, cmd, { |t, r, msg|
				func.value(msg[1..])
			}).add;
		);
	}
	
	*addResponderWithReply { |addr, cmd, func|
		responders = responders.add(
			OSCresponderNode(addr, cmd, { |t, r, msg, addr|
				var res = func.value(msg[2..]);
				var id = msg[1];
				this.sendReply(addr, id, res);
			}).add;
		);
	}
	
	*removeResponder { |cmd|
		var all = responders.select { |resp| resp.cmdName == cmd };
		all.do { |resp|
			resp.remove;
			responders.remove(resp);
		}
	}
	
	// if lookup == true, use "name_key" lookup scheme
	
	*getObject { |name|
		var object, objectName, key;
		^objects.at(name) ?? {
			if(lookup) {
				#objectName ... key = name.asString.split($_);
				object = objects.at(objectName.asSymbol);
				object.at(key.join($_).asSymbol);
			}
		}
	}
	
	// name, selector, args ...
	
	*oscPerform { |msg|
			var name, selector, args, receiver;
			#name, selector ... args = msg;
			receiver = this.getObject(name);
			^if(receiver.isNil) { 
				"OpenObject: name: % not found".format(name).warn;
				nil
			} {
				args = args.unfoldOSCArrays;
				receiver.performList(selector, args)
			}
	}
	
	// name, selector, argName1, val1, argName2, val2 ...
	
	*oscPerformKeyValuePairs { |msg|
			var name, selector, args, receiver;
			#name, selector ... args = msg;
			receiver = this.getObject(name);
			^if(receiver.isNil) {
				"OpenObject: name: % not found".format(name).warn;
				nil
			} {
				args = args.unfoldOSCArrays;
				receiver.performKeyValuePairs(selector, args)
			}
	}
	
	// name1, sourceCode1, name2, sourceCode2  ...
	
	*setProxySource { |msg|
			
			msg.pairsDo { |name, string|
				var object, receiver;
				receiver = this.getObject(name);
				string.postcs;
				
				if(receiver.isNil) { 
					"OpenObject: name: % not found".format(name).warn 
				} {
					object = string.asString.interpret;
					object !? { receiver.source_(object) };
				}
			}
	}
	
	// evaluate an array of strings and return the results
	
	*interpretOSC { |msg|
			^msg.collect { |string| value(interpret(asString(string))) };
	}
	
	// send an array of values back to a sender
	
	*sendReply { |addr, id, args|
		args = args.asOSCArgArray;
		addr.sendMsg("/oo_reply", id, *args);
	}
	
}



/////////////////////// method extensions ///////////////////////

+ Object {

	publish { |name|
		if(OpenObject.objects.at(name).notNil) { 
			"OpenObjects: overriding object with this name: %".format(name).warn 
		};
		OpenObject.put(name, this)
	}
	
	unpublish { |name|
		OpenObject.remove(this)
	}
	
}

+ Ndef {

	*at { arg key;
		^this.dictFor(Server.default).at(key)
	}

}
