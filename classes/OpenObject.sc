
OpenObject {

	classvar <>objects;
	classvar <responders;
	classvar <>findProxies = true;
	
	*initClass {
		objects = ();
	}
	
	*put { |name, object|
		objects.put(name, object)
	}
	
	*remove { |object|
		var key = objects.findKeyForValue(object);
		key !? { objects.removeAt(key) }
	}

	*start { |addr|
		if(responders.notNil) { "OpenObject: already running".warn; ^this };
		this.addResponder(addr, '/oo', { |msg| this.oscPerform(msg) });
		this.addResponder(addr, '/ook', { |msg| this.oscPerformKeyValuePairs(msg) });
	}
	
	*end {
		responders.do(_.remove);
		responders = nil;
	}
	
	*addResponder { |addr, cmd, func|
		responders = responders.add(
			OSCresponderNode(addr, cmd, { |t, r, msg|
				func.value(msg[1..])
			}).add;
		);
	}
	
	*findObject { |name|
		var res = objects.at(name);
		var class, key;
		
		^res ?? {
			if(findProxies) {
				#class ... key = name.asString.split($_);
				class.asSymbol.asClass.at(key.join($_).asSymbol);
			}
		}
	}
	
	*oscPerform { |msg|
			var name, selector, args, receiver;
			#name, selector ... args = msg;
			receiver = this.findObject(name);
			if(receiver.isNil) { 
				"OpenObject: name: % not found".format(name).warn
			} {
				args = args.unfoldOSCArrays;
				receiver.performList(selector, args)
			}
	}
	
	*oscPerformKeyValuePairs { |msg|
			var name, selector, args, receiver;
			#name, selector ... args = msg;
			receiver = this.findObject(name);
			if(receiver.isNil) {
				"OpenObject: name: % not found".format(name).warn 
			} {
				args = args.unfoldOSCArrays;
				receiver.performKeyValuePairs(selector, args)
			}
	}
	
	// a dangerous tool for both good and evil ...
	
	*openInterpreterFor { |addr, cmd, selector|
			this.addResponder(addr, cmd, { |msg| 
				this.oscInterpret(selector, msg) 
			});
	}
	
	*oscInterpret { |selector, msg|
			var name, string, receiver;
			name = msg[0];
			string = msg[1..].join;
			receiver = objects.at(name);
			string.postcs;
			
			if(receiver.isNil) { 
				"OpenObject: name: % not found".format(name).warn 
			} {
				receiver.perform(selector, string.interpret);
			}
	}
	
	
}


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

// arrayed osc argument support

+ SequenceableCollection { // todo: make this recursive

	unfoldOSCArrays {
		var res, current;
		this.do { |elem|
			if(elem === '['/*]*/) {
				current !? { res = res.add(current) };
				current = nil;
			} {
				if(elem == /*[*/ ']') {
					res = res.add(current);
					current = nil;
				} {
					current = current.add(elem);
				}
			}
		};
		current !? { res = res.add(current) };
		^res.collect(_.unbubble).unbubble
	}
	
}

