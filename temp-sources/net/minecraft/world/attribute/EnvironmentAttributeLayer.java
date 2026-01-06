package net.minecraft.world.attribute;

import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public sealed interface EnvironmentAttributeLayer<Value>
	permits EnvironmentAttributeLayer.Constant,
	EnvironmentAttributeLayer.TimeBased,
	EnvironmentAttributeLayer.Positional {
	@FunctionalInterface
	public non-sealed interface Constant<Value> extends EnvironmentAttributeLayer<Value> {
		Value applyConstant(Value object);
	}

	@FunctionalInterface
	public non-sealed interface Positional<Value> extends EnvironmentAttributeLayer<Value> {
		Value applyPositional(Value object, Vec3 vec3, @Nullable SpatialAttributeInterpolator spatialAttributeInterpolator);
	}

	@FunctionalInterface
	public non-sealed interface TimeBased<Value> extends EnvironmentAttributeLayer<Value> {
		Value applyTimeBased(Value object, int i);
	}
}
